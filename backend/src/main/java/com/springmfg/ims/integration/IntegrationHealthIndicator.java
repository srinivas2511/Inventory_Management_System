package com.springmfg.ims.integration;

import java.util.List;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.springmfg.ims.integration.webhook.domain.WebhookDelivery;
import com.springmfg.ims.integration.webhook.repository.WebhookDeliveryRepository;

/**
 * Webhook backlog for the health endpoint. It is deliberately not part of the readiness group and never reports DOWN:
 * a slow or failing integration must not take the application out of service (PLAN R19-7.1).
 */
@Component("integration")
public class IntegrationHealthIndicator implements HealthIndicator {

    private final WebhookDeliveryRepository deliveries;

    public IntegrationHealthIndicator(WebhookDeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    @Override
    public Health health() {
        try {
            long waiting = deliveries.countByStatusIn(List.of(WebhookDelivery.PENDING, WebhookDelivery.RETRY));
            long failed = deliveries.countByStatusIn(List.of(WebhookDelivery.FAILED));
            return Health.up().withDetail("webhooksWaiting", waiting).withDetail("webhooksFailed", failed).build();
        } catch (RuntimeException e) {
            return Health.unknown().withDetail("error", e.getClass().getSimpleName()).build();
        }
    }
}
