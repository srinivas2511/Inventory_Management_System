package com.mfg.ims.integration.webhook;

import com.mfg.ims.common.net.WebhookTargetValidator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.insight.alert.AlertService;
import com.mfg.ims.integration.webhook.domain.WebhookDelivery;
import com.mfg.ims.integration.webhook.domain.WebhookSubscription;
import com.mfg.ims.integration.webhook.repository.WebhookDeliveryRepository;
import com.mfg.ims.integration.webhook.repository.WebhookSubscriptionRepository;
import com.mfg.ims.integration.webhook.service.SecretCipher;
import com.mfg.ims.integration.webhook.service.WebhookDeliveryService;
import com.mfg.ims.integration.webhook.service.WebhookDeliveryService.Outcome;
import com.mfg.ims.integration.webhook.service.WebhookHttpClient;
import com.mfg.ims.integration.webhook.service.WebhookSigner;

/** The retry schedule and outcomes, driven with a fake HTTP client and no database or clock dependence. */
class WebhookDeliveryServiceTest {

    private static final String SECRET = "whsec_unit";

    private final WebhookDeliveryRepository deliveries = mock(WebhookDeliveryRepository.class);
    private final WebhookSubscriptionRepository subscriptions = mock(WebhookSubscriptionRepository.class);
    private final AlertService alerts = mock(AlertService.class);
    private final SystemSettingService settings = mock(SystemSettingService.class);
    private final SecretCipher cipher = SecretCipher.withKey(new byte[32]);
    private final FakeHttp http = new FakeHttp();
    private WebhookDeliveryService service;
    private WebhookSubscription sub;
    private WebhookDelivery delivery;

    static class FakeHttp implements WebhookHttpClient {
        int status = 200;
        IOException failure;
        final List<Map<String, String>> headers = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        final List<String> urls = new ArrayList<>();

        @Override
        public Response post(String url, Map<String, String> h, String body, Duration timeout) throws IOException {
            urls.add(url);
            headers.add(h);
            bodies.add(body);
            if (failure != null) {
                throw failure;
            }
            return new Response(status, "answer " + status);
        }
    }

    @BeforeEach
    void setUp() {
        when(settings.getInt(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(i -> i.getArgument(1));
        service = new WebhookDeliveryService(deliveries, subscriptions, http, new WebhookTargetValidator(true), cipher,
                alerts, settings, new ObjectMapper());
        sub = new WebhookSubscription();
        ReflectionTestUtils.setField(sub, "id", 5L);
        sub.setName("ERP sink");
        sub.setTargetUrl("http://127.0.0.1:9/hook");
        sub.setSecretCipher(cipher.encrypt(SECRET));
        sub.setSecretHint("unit");
        sub.setEvents("*");
        sub.setActive(true);
        delivery = new WebhookDelivery();
        ReflectionTestUtils.setField(delivery, "id", 11L);
        delivery.setSubscriptionId(5L);
        delivery.setEventType("StockPosted");
        delivery.setEventKey("StockPosted:42");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", 42);
        delivery.setPayload(payload);
        delivery.setNextRetryAt(Instant.now().minusSeconds(1));
        when(deliveries.findByIdForUpdate(11L)).thenReturn(Optional.of(delivery));
        when(subscriptions.findById(5L)).thenReturn(Optional.of(sub));
    }

    @Test
    void backoffIsOneFiveFifteenMinutesThenOneFourAndTwentyFourHours() {
        assertThat(WebhookDeliveryService.backoffAfter(0)).isNull();
        assertThat(WebhookDeliveryService.backoffAfter(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(WebhookDeliveryService.backoffAfter(2)).isEqualTo(Duration.ofMinutes(5));
        assertThat(WebhookDeliveryService.backoffAfter(3)).isEqualTo(Duration.ofMinutes(15));
        assertThat(WebhookDeliveryService.backoffAfter(4)).isEqualTo(Duration.ofHours(1));
        assertThat(WebhookDeliveryService.backoffAfter(5)).isEqualTo(Duration.ofHours(4));
        assertThat(WebhookDeliveryService.backoffAfter(6)).isEqualTo(Duration.ofHours(24));
        assertThat(WebhookDeliveryService.backoffAfter(7)).as("the initial attempt plus six retries is the limit").isNull();
    }

    @Test
    void aSuccessfulDeliveryIsSignedAndMarkedDelivered() {
        assertThat(service.deliver(11L)).isEqualTo(Outcome.DELIVERED);

        assertThat(delivery.getStatus()).isEqualTo(WebhookDelivery.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isNotNull();
        assertThat(delivery.getResponseStatus()).isEqualTo(200);
        assertThat(delivery.getNextRetryAt()).isNull();
        Map<String, String> h = http.headers.get(0);
        assertThat(h).containsEntry("X-IMS-Event", "StockPosted").containsEntry("X-IMS-Event-Key", "StockPosted:42")
                .containsEntry("X-IMS-Delivery", "11").containsEntry("Content-Type", "application/json");
        assertThat(WebhookSigner.verify(SECRET, h.get("X-IMS-Signature"), http.bodies.get(0), 60, Instant.now()))
                .as("the receiver can verify the signature with the shared secret").isTrue();
        assertThat(http.bodies.get(0)).contains("\"type\":\"StockPosted\"").contains("\"transactionId\":42");
        verify(alerts).resolveOpen(eq("WEBHOOK_DELIVERY_FAILED"), eq("WEBHOOK_SUBSCRIPTION"), eq(5L), anyString());
    }

    @Test
    void eachFailureSchedulesTheNextRetryOnTheBackoffLadderThenTheSeventhFails() {
        http.status = 500;
        Duration[] ladder = {Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1),
                Duration.ofHours(4), Duration.ofHours(24)};
        for (int failure = 1; failure <= 6; failure++) {
            delivery.setNextRetryAt(Instant.now().minusSeconds(1));
            assertThat(service.deliver(11L)).isEqualTo(Outcome.RETRY_SCHEDULED);
            assertThat(delivery.getStatus()).isEqualTo(WebhookDelivery.RETRY);
            assertThat(delivery.getRetryCount()).isEqualTo((short) failure);
            Duration wait = Duration.between(delivery.getAttemptedAt(), delivery.getNextRetryAt());
            assertThat(wait).as("wait after failure %d", failure).isEqualTo(ladder[failure - 1]);
            assertThat(delivery.getLastError()).contains("HTTP 500");
        }
        verify(alerts, never()).raise(anyString(), anyString(), anyString(), anyLong(), anyString());

        delivery.setNextRetryAt(Instant.now().minusSeconds(1));
        assertThat(service.deliver(11L)).isEqualTo(Outcome.FAILED);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDelivery.FAILED);
        assertThat(delivery.getNextRetryAt()).isNull();
        assertThat(delivery.getRetryCount()).isEqualTo((short) 7);
        verify(alerts).raise(eq("WEBHOOK_DELIVERY_FAILED"), eq("HIGH"), eq("WEBHOOK_SUBSCRIPTION"), eq(5L),
                org.mockito.ArgumentMatchers.contains("ERP sink"));
        assertThat(http.bodies).hasSize(7);
    }

    @Test
    void aDeliveryNotYetDueIsLeftAlone() {
        delivery.setStatus(WebhookDelivery.RETRY);
        delivery.setNextRetryAt(Instant.now().plusSeconds(120));
        assertThat(service.deliver(11L)).isEqualTo(Outcome.SKIPPED);
        assertThat(http.bodies).isEmpty();
    }

    @Test
    void settledDeliveriesAreNotSentAgain() {
        delivery.setStatus(WebhookDelivery.DELIVERED);
        assertThat(service.deliver(11L)).isEqualTo(Outcome.SKIPPED);
        delivery.setStatus(WebhookDelivery.FAILED);
        assertThat(service.deliver(11L)).isEqualTo(Outcome.SKIPPED);
        assertThat(http.bodies).isEmpty();
    }

    @Test
    void aNetworkErrorIsRecordedWithoutTheStackTraceAndRetried() {
        http.failure = new IOException("connect timed out\n\tat sun.nio.ch.Net.connect(Net.java:1)");
        assertThat(service.deliver(11L)).isEqualTo(Outcome.RETRY_SCHEDULED);
        assertThat(delivery.getLastError()).isEqualTo("IOException: connect timed out");
        assertThat(delivery.getResponseStatus()).isNull();
    }

    @Test
    void aTargetThatNowFailsTheSsrfCheckIsNeverCalled() {
        sub.setTargetUrl("https://169.254.169.254/latest");
        WebhookDeliveryService strict = new WebhookDeliveryService(deliveries, subscriptions, http,
                new WebhookTargetValidator(false), cipher, alerts, settings, new ObjectMapper());
        assertThat(strict.deliver(11L)).isEqualTo(Outcome.RETRY_SCHEDULED);
        assertThat(http.urls).as("nothing was sent").isEmpty();
        assertThat(delivery.getLastError()).contains("Webhook target refused");
    }

    @Test
    void aDeactivatedSubscriptionFailsItsQueuedDeliveries() {
        sub.setActive(false);
        assertThat(service.deliver(11L)).isEqualTo(Outcome.FAILED);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDelivery.FAILED);
        assertThat(delivery.getLastError()).contains("inactive");
        assertThat(http.bodies).isEmpty();
    }

    @Test
    void anUndecryptableSecretIsAFailureNotACrash() {
        sub.setSecretCipher("AAAA");
        assertThat(service.deliver(11L)).isEqualTo(Outcome.RETRY_SCHEDULED);
        assertThat(delivery.getLastError()).startsWith("IllegalStateException");
        assertThat(http.bodies).isEmpty();
        verify(deliveries, never()).save(any());
    }
}
