package com.mfg.ims.integration.webhook.service;

import com.mfg.ims.common.net.WebhookTargetValidator;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ConflictException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.integration.webhook.domain.WebhookDelivery;
import com.mfg.ims.integration.webhook.domain.WebhookSubscription;
import com.mfg.ims.integration.webhook.dto.WebhookDtos.SaveWebhookRequest;
import com.mfg.ims.integration.webhook.dto.WebhookDtos.WebhookDeliveryDto;
import com.mfg.ims.integration.webhook.dto.WebhookDtos.WebhookDto;
import com.mfg.ims.integration.webhook.dto.WebhookDtos.WebhookSecretDto;
import com.mfg.ims.integration.webhook.repository.WebhookDeliveryRepository;
import com.mfg.ims.integration.webhook.repository.WebhookSubscriptionRepository;

@Service
public class WebhookAdminService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookTargetValidator validator;
    private final SecretCipher cipher;
    private final WebhookEnqueueService enqueue;
    private final WebhookDeliveryService delivery;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public WebhookAdminService(WebhookSubscriptionRepository subscriptions, WebhookDeliveryRepository deliveries,
                               WebhookTargetValidator validator, SecretCipher cipher, WebhookEnqueueService enqueue,
                               WebhookDeliveryService delivery, AuditService audit,
                               PlatformTransactionManager transactionManager) {
        this.subscriptions = subscriptions;
        this.deliveries = deliveries;
        this.validator = validator;
        this.cipher = cipher;
        this.enqueue = enqueue;
        this.delivery = delivery;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<WebhookDto> list() {
        return subscriptions.findAll(Sort.by("id")).stream().map(WebhookAdminService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public WebhookDto get(Long id) {
        return toDto(require(id));
    }

    public List<String> eventTypes() {
        return WebhookEventTypes.SUBSCRIBABLE;
    }

    @Transactional
    public WebhookSecretDto create(SaveWebhookRequest req) {
        validator.validate(req.targetUrl());
        String events = normaliseEvents(req.events());
        String secret = newSecret();
        WebhookSubscription s = new WebhookSubscription();
        s.setWebhookCode(newCode());
        s.setName(req.name().trim());
        s.setTargetUrl(req.targetUrl().trim());
        s.setEvents(events);
        s.setActive(req.active() == null || req.active());
        storeSecret(s, secret);
        subscriptions.save(s);
        audit.record("WEBHOOK_CREATED", "WEBHOOK", String.valueOf(s.getId()), null,
                "{\"code\":\"" + s.getWebhookCode() + "\",\"events\":\"" + s.getEvents() + "\"}", null);
        return new WebhookSecretDto(toDto(s), secret);
    }

    @Transactional
    public WebhookDto update(Long id, SaveWebhookRequest req) {
        WebhookSubscription s = require(id);
        if (req.version() != null && !req.version().equals(s.getVersion())) {
            throw new ConflictException(ErrorCode.VERSION_CONFLICT, "The webhook was changed by someone else");
        }
        validator.validate(req.targetUrl());
        String old = s.getTargetUrl() + " | " + s.getEvents() + " | " + (s.isActive() ? "active" : "inactive");
        s.setName(req.name().trim());
        s.setTargetUrl(req.targetUrl().trim());
        s.setEvents(normaliseEvents(req.events()));
        if (req.active() != null) {
            s.setActive(req.active());
        }
        subscriptions.save(s);
        audit.record("WEBHOOK_UPDATED", "WEBHOOK", String.valueOf(id), old,
                s.getTargetUrl() + " | " + s.getEvents() + " | " + (s.isActive() ? "active" : "inactive"), null);
        return toDto(s);
    }

    @Transactional
    public WebhookSecretDto rotateSecret(Long id) {
        WebhookSubscription s = require(id);
        String secret = newSecret();
        storeSecret(s, secret);
        subscriptions.save(s);
        audit.record("WEBHOOK_SECRET_ROTATED", "WEBHOOK", String.valueOf(id), null, null, null);
        return new WebhookSecretDto(toDto(s), secret);
    }

    /** Queues a Ping for this subscription and sends it at once, so the result is visible to the administrator. */
    public WebhookDeliveryDto sendTest(Long id) {
        WebhookSubscription s = require(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "Test event from IMS");
        data.put("webhook", s.getWebhookCode());
        Long deliveryId = enqueue.enqueueFor(id, WebhookEventTypes.PING, "Ping:" + UUID.randomUUID(), data);
        delivery.deliver(deliveryId);
        return toDto(deliveries.findById(deliveryId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryDto> deliveries(Long subscriptionId, String status, Pageable pageable) {
        String s = status == null || status.isBlank() ? "" : status.trim().toUpperCase();
        return deliveries.search(subscriptionId == null ? 0L : subscriptionId, s, pageable).map(WebhookAdminService::toDto);
    }

    /** Puts a failed or waiting delivery back on the schedule, restarts its retries and sends it now. */
    public WebhookDeliveryDto retry(Long deliveryId) {
        tx.executeWithoutResult(status -> resetForRetry(deliveryId));
        delivery.deliver(deliveryId);
        return toDto(deliveries.findById(deliveryId).orElseThrow());
    }

    private void resetForRetry(Long deliveryId) {
        WebhookDelivery d = deliveries.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new NotFoundException("Webhook delivery", deliveryId));
        if (WebhookDelivery.DELIVERED.equals(d.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION, "The delivery already succeeded");
        }
        String old = d.getStatus();
        d.setStatus(WebhookDelivery.RETRY);
        d.setRetryCount((short) 0);
        d.setNextRetryAt(Instant.now());
        deliveries.save(d);
        audit.record("WEBHOOK_DELIVERY_RETRIED", "WEBHOOK_DELIVERY", String.valueOf(deliveryId), old,
                WebhookDelivery.RETRY, null);
    }

    private WebhookSubscription require(Long id) {
        return subscriptions.findById(id).orElseThrow(() -> new NotFoundException("Webhook", id));
    }

    private void storeSecret(WebhookSubscription s, String secret) {
        s.setSecretCipher(cipher.encrypt(secret));
        s.setSecretHint(secret.substring(secret.length() - 4));
    }

    static String normaliseEvents(List<String> requested) {
        Set<String> events = new LinkedHashSet<>();
        for (String raw : requested) {
            String e = raw.trim();
            if (e.equals(WebhookEventTypes.ALL)) {
                return WebhookEventTypes.ALL;
            }
            if (!WebhookEventTypes.SUBSCRIBABLE.contains(e)) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Unknown event type: " + e
                        + " (allowed: " + String.join(", ", WebhookEventTypes.SUBSCRIBABLE) + " or *)");
            }
            events.add(e);
        }
        return String.join(",", events);
    }

    private String newCode() {
        String code;
        do {
            byte[] b = new byte[4];
            RANDOM.nextBytes(b);
            code = "wh-" + HexFormat.of().formatHex(b);
        } while (subscriptions.existsByWebhookCode(code));
        return code;
    }

    static String newSecret() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static WebhookDto toDto(WebhookSubscription s) {
        List<String> events = new ArrayList<>(Arrays.asList(s.getEvents().split(",")));
        return new WebhookDto(s.getId(), s.getWebhookCode(), s.getName(), s.getTargetUrl(), s.getSecretHint(), events,
                s.isActive(), s.getCreatedAt(), s.getVersion());
    }

    static WebhookDeliveryDto toDto(WebhookDelivery d) {
        return new WebhookDeliveryDto(d.getId(), d.getSubscriptionId(), d.getEventType(), d.getEventKey(), d.getStatus(),
                d.getRetryCount(), d.getNextRetryAt(), d.getAttemptedAt(), d.getDeliveredAt(), d.getResponseStatus(),
                d.getResponseBody(), d.getLastError(), d.getCreatedAt());
    }
}
