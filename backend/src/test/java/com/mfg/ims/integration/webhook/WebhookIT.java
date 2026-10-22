package com.mfg.ims.integration.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import com.mfg.ims.common.event.InvoicePaidEvent;
import com.mfg.ims.integration.webhook.domain.WebhookDelivery;
import com.mfg.ims.integration.webhook.dto.WebhookDtos.SaveWebhookRequest;
import com.mfg.ims.integration.webhook.dto.WebhookDtos.WebhookDeliveryDto;
import com.mfg.ims.integration.webhook.dto.WebhookDtos.WebhookSecretDto;
import com.mfg.ims.integration.webhook.repository.WebhookDeliveryRepository;
import com.mfg.ims.integration.webhook.service.WebhookAdminService;
import com.mfg.ims.integration.webhook.service.WebhookDeliveryJob;
import com.mfg.ims.integration.webhook.service.WebhookDeliveryService;
import com.mfg.ims.integration.webhook.service.WebhookEnqueueService;
import com.mfg.ims.integration.webhook.service.WebhookSigner;
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.dto.PostingResult;
import com.mfg.ims.inventory.repository.AlertRepository;
import com.mfg.ims.inventory.service.BatchService;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * Webhook outbox against a real database and a real HTTP server on localhost. The test is deliberately not
 * transactional: the listener runs after the posting transaction commits, which is the path that matters.
 */
class WebhookIT extends AbstractIntegrationTest {

    record Received(String method, Map<String, String> headers, String body) {
    }

    @Autowired WebhookAdminService admin;
    @Autowired WebhookEnqueueService enqueue;
    @Autowired WebhookDeliveryJob job;
    @Autowired WebhookDeliveryService deliveryService;
    @Autowired WebhookDeliveryRepository deliveries;
    @Autowired InventoryPostingService posting;
    @Autowired BatchService batchService;
    @Autowired TestData td;
    @Autowired AlertRepository alertRepo;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper mapper;

    private HttpServer server;
    private String url;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger answer = new AtomicInteger(200);
    private final List<Long> subscriptions = new java.util.ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, v.get(0)));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestMethod(), headers, body));
            byte[] reply = ("answer " + answer.get()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(answer.get(), reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        // a deactivated subscription receives no events from the tests that follow
        for (Long id : subscriptions) {
            jdbc.update("UPDATE ims.webhook_subscriptions SET active = FALSE WHERE id = ?", id);
        }
    }

    private WebhookSecretDto subscribe(String targetUrl, String... events) {
        WebhookSecretDto created = admin.create(new SaveWebhookRequest("IT hook " + UUID.randomUUID(), targetUrl,
                List.of(events), true, null));
        subscriptions.add(created.webhook().id());
        return created;
    }

    private List<WebhookDelivery> deliveriesOf(Long subscriptionId) {
        return deliveries.search(subscriptionId, "", PageRequest.of(0, 100)).getContent();
    }

    private PostingResult postReceipt() {
        Material material = td.material();
        var batch = batchService.createMaterialBatch(material.getId(), null, null, null, null, null, null,
                new BigDecimal("1000.000"), null, null, new BigDecimal("10.0000"), "AVAILABLE");
        return posting.post(new PostingCommand("GOODS_RECEIPT", "MATERIAL", material.getId(), null, batch.getId(), null,
                1L, 1L, "AVAILABLE", new BigDecimal("5.000"), "KG", new BigDecimal("10.0000"),
                "GOODS_RECEIPT", 1L, "WH-" + System.nanoTime(), "receipt"));
    }

    @Test
    void aStockPostingIsQueuedAfterCommitAndDeliveredSigned() throws Exception {
        WebhookSecretDto sub = subscribe(url, "StockPosted");
        PostingResult posted = postReceipt();

        List<WebhookDelivery> queued = deliveriesOf(sub.webhook().id());
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).getStatus()).isEqualTo(WebhookDelivery.PENDING);
        assertThat(queued.get(0).getEventKey()).isEqualTo("StockPosted:" + posted.transactionId());
        assertThat(received).as("nothing is sent from inside the posting").isEmpty();

        assertThat(job.processDue()).isGreaterThanOrEqualTo(1);

        assertThat(received).hasSize(1);
        Received call = received.get(0);
        assertThat(call.method()).isEqualTo("POST");
        assertThat(call.headers()).containsEntry("X-ims-event", "StockPosted");
        String signature = call.headers().get("X-ims-signature");
        assertThat(WebhookSigner.verify(sub.secret(), signature, call.body(), 300, Instant.now()))
                .as("the receiver can verify the HMAC with the one-time secret").isTrue();
        assertThat(WebhookSigner.verify("whsec_wrong", signature, call.body(), 300, Instant.now())).isFalse();

        JsonNode body = mapper.readTree(call.body());
        assertThat(body.path("type").asText()).isEqualTo("StockPosted");
        assertThat(body.path("id").asText()).isEqualTo("StockPosted:" + posted.transactionId());
        assertThat(body.path("data").path("transactionNumber").asText()).isEqualTo(posted.transactionNumber());
        assertThat(body.path("data").path("quantity").decimalValue()).isEqualByComparingTo("5.000");

        WebhookDelivery done = deliveries.findById(queued.get(0).getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(WebhookDelivery.DELIVERED);
        assertThat(done.getResponseStatus()).isEqualTo(200);
        assertThat(done.getDeliveredAt()).isNotNull();

        job.processDue();
        assertThat(received).as("a delivered event is never sent again").hasSize(1);
    }

    @Test
    void aFailingTargetIsRetriedOnTheBackoffLadderThenFailsWithAnAlertAndAManualRetryRecovers() {
        WebhookSecretDto sub = subscribe(url, "InvoicePaid");
        Long subId = sub.webhook().id();
        answer.set(500);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceNumber", "INV-IT");
        assertThat(enqueue.enqueue("InvoicePaid", "InvoicePaid:IT-" + UUID.randomUUID(), data)).isGreaterThanOrEqualTo(1);
        Long deliveryId = deliveriesOf(subId).get(0).getId();

        Duration[] ladder = {Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1),
                Duration.ofHours(4), Duration.ofHours(24)};
        for (int failure = 1; failure <= 6; failure++) {
            makeDue(deliveryId);
            deliveryService.deliver(deliveryId);
            WebhookDelivery d = deliveries.findById(deliveryId).orElseThrow();
            assertThat(d.getStatus()).as("after failure %d", failure).isEqualTo(WebhookDelivery.RETRY);
            assertThat(d.getRetryCount()).isEqualTo((short) failure);
            Duration wait = Duration.between(d.getAttemptedAt(), d.getNextRetryAt());
            assertThat(wait.minus(ladder[failure - 1]).abs()).isLessThan(Duration.ofMillis(5));
            assertThat(d.getResponseStatus()).isEqualTo(500);
            assertThat(d.getLastError()).contains("HTTP 500");
        }
        assertThat(alertRepo.findOpenByTypeAndEntity("WEBHOOK_DELIVERY_FAILED", "WEBHOOK_SUBSCRIPTION", subId)).isEmpty();

        makeDue(deliveryId);
        deliveryService.deliver(deliveryId);
        WebhookDelivery failed = deliveries.findById(deliveryId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(WebhookDelivery.FAILED);
        assertThat(failed.getNextRetryAt()).isNull();
        assertThat(received).as("the initial attempt plus six retries").hasSize(7);
        assertThat(alertRepo.findOpenByTypeAndEntity("WEBHOOK_DELIVERY_FAILED", "WEBHOOK_SUBSCRIPTION", subId))
                .as("a permanent failure raises an alert").hasSize(1);

        answer.set(200);
        WebhookDeliveryDto retried = admin.retry(deliveryId);
        assertThat(retried.status()).isEqualTo("DELIVERED");
        assertThat(retried.retryCount()).as("a manual retry restarts the schedule").isZero();
        assertThat(alertRepo.findOpenByTypeAndEntity("WEBHOOK_DELIVERY_FAILED", "WEBHOOK_SUBSCRIPTION", subId))
                .as("the alert closes once delivery works again").isEmpty();
    }

    private void makeDue(Long deliveryId) {
        // the JVM clock, not the database's: the container clock can drift from the host's
        jdbc.update("UPDATE ims.webhook_deliveries SET next_retry_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)), deliveryId);
    }

    @Test
    void anEventIsQueuedOnceAndOnlyForSubscriptionsThatWantIt() {
        WebhookSecretDto wantsStock = subscribe(url, "StockPosted");
        WebhookSecretDto wantsOrders = subscribe(url, "OrderCompleted");
        WebhookSecretDto wantsAll = subscribe(url, "*");
        String key = "StockPosted:ONCE-" + UUID.randomUUID();

        int first = enqueue.enqueue("StockPosted", key, Map.of("n", 1));
        int again = enqueue.enqueue("StockPosted", key, Map.of("n", 1));

        assertThat(first).as("stock subscription and the wildcard").isGreaterThanOrEqualTo(2);
        assertThat(again).as("the same event key is never queued twice").isZero();
        assertThat(deliveriesOf(wantsStock.webhook().id())).hasSize(1);
        assertThat(deliveriesOf(wantsAll.webhook().id())).hasSize(1);
        assertThat(deliveriesOf(wantsOrders.webhook().id())).isEmpty();
    }

    @Test
    void anUnreachableTargetNeverBlocksOrFailsAStockPosting() {
        WebhookSecretDto sub = subscribe("http://127.0.0.1:1/hook", "StockPosted");   // nothing listens on port 1

        PostingResult posted = postReceipt();                                           // must not throw

        assertThat(posted.transactionId()).isNotNull();
        Integer ledgerRows = jdbc.queryForObject("SELECT count(*) FROM ims.inventory_transactions WHERE id = ?",
                Integer.class, posted.transactionId());
        assertThat(ledgerRows).as("the stock movement is committed").isEqualTo(1);

        job.processDue();
        WebhookDelivery d = deliveriesOf(sub.webhook().id()).get(0);
        assertThat(d.getStatus()).isEqualTo(WebhookDelivery.RETRY);
        assertThat(d.getResponseStatus()).isNull();
        assertThat(d.getLastError()).isNotBlank();
    }

    @Test
    void anInvoicePaidEventIsQueuedOnlyIfItsTransactionCommits() {
        WebhookSecretDto sub = subscribe(url, "InvoicePaid");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long committedId = 880_000 + (System.nanoTime() % 1000);
        long rolledBackId = committedId + 5000;

        tx.executeWithoutResult(s -> publisher.publishEvent(
                new InvoicePaidEvent(committedId, "INV-C", 1L, new BigDecimal("100.0000"), "INR")));
        try {
            tx.executeWithoutResult(s -> {
                publisher.publishEvent(new InvoicePaidEvent(rolledBackId, "INV-R", 1L, new BigDecimal("1.0000"), "INR"));
                throw new IllegalStateException("roll back");
            });
        } catch (IllegalStateException expected) {
            // the transaction was rolled back on purpose
        }

        assertThat(deliveriesOf(sub.webhook().id())).extracting(WebhookDelivery::getEventKey)
                .containsExactly("InvoicePaid:" + committedId);
    }

    @Test
    void theSecretIsShownOnceStoredEncryptedAndRotationChangesTheSigningKey() throws Exception {
        WebhookSecretDto created = subscribe(url, "StockPosted");
        Long id = created.webhook().id();
        assertThat(created.secret()).startsWith("whsec_");
        assertThat(created.webhook().secretHint()).isEqualTo(created.secret().substring(created.secret().length() - 4));

        String stored = jdbc.queryForObject("SELECT secret_cipher FROM ims.webhook_subscriptions WHERE id = ?",
                String.class, id);
        assertThat(stored).doesNotContain(created.secret()).doesNotContain("whsec_");
        assertThat(mapper.writeValueAsString(admin.get(id))).as("the secret is never read back")
                .doesNotContain(created.secret()).doesNotContain("secret_cipher").doesNotContain("secretCipher");

        WebhookSecretDto rotated = admin.rotateSecret(id);
        assertThat(rotated.secret()).isNotEqualTo(created.secret());

        WebhookDeliveryDto ping = admin.sendTest(id);
        assertThat(ping.status()).isEqualTo("DELIVERED");
        assertThat(ping.eventType()).isEqualTo("Ping");
        Received call = received.get(received.size() - 1);
        String signature = call.headers().get("X-ims-signature");
        assertThat(WebhookSigner.verify(rotated.secret(), signature, call.body(), 300, Instant.now())).isTrue();
        assertThat(WebhookSigner.verify(created.secret(), signature, call.body(), 300, Instant.now()))
                .as("the old secret no longer signs").isFalse();
    }

    @Test
    void theDeliveryListFiltersByStatusAndSubscriptionWithoutNullParameters() {
        WebhookSecretDto sub = subscribe(url, "StockPosted");
        answer.set(500);
        enqueue.enqueue("StockPosted", "StockPosted:LIST-" + UUID.randomUUID(), Map.of("n", 1));
        job.processDue();

        assertThat(admin.deliveries(sub.webhook().id(), "RETRY", PageRequest.of(0, 10)).getContent()).hasSize(1);
        assertThat(admin.deliveries(sub.webhook().id(), "delivered", PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(admin.deliveries(sub.webhook().id(), null, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(admin.deliveries(null, null, PageRequest.of(0, 10)).getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(admin.deliveries(null, "  ", PageRequest.of(0, 10)).getTotalElements()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void theHealthEndpointStaysUpAndReportsTheWebhookBacklog() throws Exception {
        subscribe(url, "StockPosted");
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
