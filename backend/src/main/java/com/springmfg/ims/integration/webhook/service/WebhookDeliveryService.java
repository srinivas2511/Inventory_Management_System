package com.springmfg.ims.integration.webhook.service;

import com.springmfg.ims.common.net.WebhookTargetValidator;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.insight.alert.AlertService;
import com.springmfg.ims.integration.webhook.domain.WebhookDelivery;
import com.springmfg.ims.integration.webhook.domain.WebhookSubscription;
import com.springmfg.ims.integration.webhook.repository.WebhookDeliveryRepository;
import com.springmfg.ims.integration.webhook.repository.WebhookSubscriptionRepository;

/**
 * Sends one queued delivery. Each delivery is its own transaction holding a row lock, so two workers never send the
 * same one, and the outcome (delivered, a retry time, or failed) is committed whether or not the target answered.
 * Delivery is at-least-once: receivers de-duplicate on {@code X-IMS-Event-Key}.
 *
 * <p>Retry policy (DESIGN 25.3): after failure number 1..6 the next attempt is due after 1 min, 5 min, 15 min, 1 h,
 * 4 h and 24 h; a seventh failure (the initial attempt plus six retries) marks the delivery FAILED, raises an alert and
 * stops. A manual retry restarts the schedule.
 */
@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    static final Duration[] BACKOFF = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
            Duration.ofHours(1), Duration.ofHours(4), Duration.ofHours(24)};

    public static final String ALERT_TYPE = "WEBHOOK_DELIVERY_FAILED";

    public enum Outcome { DELIVERED, RETRY_SCHEDULED, FAILED, SKIPPED }

    private final WebhookDeliveryRepository deliveries;
    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookHttpClient http;
    private final WebhookTargetValidator validator;
    private final SecretCipher cipher;
    private final AlertService alerts;
    private final SystemSettingService settings;
    private final ObjectMapper mapper;

    public WebhookDeliveryService(WebhookDeliveryRepository deliveries, WebhookSubscriptionRepository subscriptions,
                                  WebhookHttpClient http, WebhookTargetValidator validator, SecretCipher cipher,
                                  AlertService alerts, SystemSettingService settings, ObjectMapper mapper) {
        this.deliveries = deliveries;
        this.subscriptions = subscriptions;
        this.http = http;
        this.validator = validator;
        this.cipher = cipher;
        this.alerts = alerts;
        this.settings = settings;
        this.mapper = mapper;
    }

    /** Delay before the retry that follows failure number {@code failures} (1-based); null once retries are spent. */
    public static Duration backoffAfter(int failures) {
        return failures >= 1 && failures <= BACKOFF.length ? BACKOFF[failures - 1] : null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome deliver(Long deliveryId) {
        WebhookDelivery d = deliveries.findByIdForUpdate(deliveryId).orElse(null);
        if (d == null || !(WebhookDelivery.PENDING.equals(d.getStatus()) || WebhookDelivery.RETRY.equals(d.getStatus()))) {
            return Outcome.SKIPPED;
        }
        Instant now = Instant.now();
        if (d.getNextRetryAt() != null && d.getNextRetryAt().isAfter(now)) {
            return Outcome.SKIPPED;
        }
        WebhookSubscription sub = subscriptions.findById(d.getSubscriptionId()).orElse(null);
        d.setAttemptedAt(now);
        if (sub == null || !sub.isActive()) {
            d.setStatus(WebhookDelivery.FAILED);
            d.setNextRetryAt(null);
            d.setLastError("The subscription is inactive");
            return Outcome.FAILED;
        }
        try {
            validator.validate(sub.getTargetUrl());
            String body = envelope(d, now);
            String secret = cipher.decrypt(sub.getSecretCipher());
            long timestamp = now.getEpochSecond();
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("User-Agent", "IMS-Webhook/1.0");
            headers.put("X-IMS-Event", d.getEventType());
            headers.put("X-IMS-Event-Key", d.getEventKey());
            headers.put("X-IMS-Delivery", String.valueOf(d.getId()));
            headers.put("X-IMS-Signature", WebhookSigner.header(secret, timestamp, body));
            Duration timeout = Duration.ofSeconds(Math.max(1, settings.getInt("integration.webhook.timeout_seconds", 10)));
            WebhookHttpClient.Response response = http.post(sub.getTargetUrl(), headers, body, timeout);
            d.setResponseStatus(response.status());
            d.setResponseBody(response.body());
            if (response.isSuccess()) {
                d.setStatus(WebhookDelivery.DELIVERED);
                d.setDeliveredAt(now);
                d.setNextRetryAt(null);
                d.setLastError(null);
                alerts.resolveOpen(ALERT_TYPE, "WEBHOOK_SUBSCRIPTION", sub.getId(), "Delivery succeeded");
                return Outcome.DELIVERED;
            }
            return failed(d, sub, "The target answered HTTP " + response.status(), now);
        } catch (IOException | RuntimeException e) {
            return failed(d, sub, describe(e), now);
        }
    }

    private Outcome failed(WebhookDelivery d, WebhookSubscription sub, String error, Instant now) {
        d.setLastError(error);
        int failures = d.getRetryCount() + 1;
        Duration wait = backoffAfter(failures);
        if (wait == null) {
            d.setRetryCount((short) failures);
            d.setStatus(WebhookDelivery.FAILED);
            d.setNextRetryAt(null);
            log.warn("Webhook delivery {} to subscription {} failed permanently: {}", d.getId(), sub.getId(), error);
            try {
                alerts.raise(ALERT_TYPE, "HIGH", "WEBHOOK_SUBSCRIPTION", sub.getId(),
                        "Webhook '" + sub.getName() + "' could not deliver " + d.getEventType() + " (" + d.getEventKey()
                                + ") after " + failures + " attempts: " + error);
            } catch (RuntimeException e) {
                log.error("Could not raise the webhook failure alert for subscription {}: {}", sub.getId(), e.toString());
            }
            return Outcome.FAILED;
        }
        d.setRetryCount((short) failures);
        d.setStatus(WebhookDelivery.RETRY);
        d.setNextRetryAt(now.plus(wait));
        log.info("Webhook delivery {} attempt {} failed, retrying at {}: {}", d.getId(), failures, d.getNextRetryAt(), error);
        return Outcome.RETRY_SCHEDULED;
    }

    private String envelope(WebhookDelivery d, Instant now) throws JsonProcessingException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", d.getEventKey());
        envelope.put("type", d.getEventType());
        envelope.put("deliveryId", d.getId());
        envelope.put("sentAt", now.toString());
        envelope.put("data", d.getPayload());
        return mapper.writeValueAsString(envelope);
    }

    /** Exception class and a bounded first line; never a stack trace, URL or address. */
    static String describe(Throwable e) {
        String message = e.getMessage();
        String first = message == null ? "" : message.lines().findFirst().orElse("");
        String text = e.getClass().getSimpleName() + (first.isBlank() ? "" : ": " + first);
        return text.length() > 400 ? text.substring(0, 400) : text;
    }
}
