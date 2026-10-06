package com.springmfg.ims.integration.webhook.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.integration.webhook.domain.WebhookDelivery;
import com.springmfg.ims.integration.webhook.domain.WebhookSubscription;
import com.springmfg.ims.integration.webhook.repository.WebhookDeliveryRepository;
import com.springmfg.ims.integration.webhook.repository.WebhookSubscriptionRepository;

/** Writes one outbox row per interested subscription. Enqueueing the same event twice is a no-op. */
@Service
public class WebhookEnqueueService {

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;

    public WebhookEnqueueService(WebhookSubscriptionRepository subscriptions, WebhookDeliveryRepository deliveries) {
        this.subscriptions = subscriptions;
        this.deliveries = deliveries;
    }

    /** Own transaction: the caller (an after-commit listener) has no transaction left to join. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int enqueue(String eventType, String eventKey, Map<String, Object> data) {
        int created = 0;
        for (WebhookSubscription s : subscriptions.findByActiveTrue()) {
            if (!WebhookEventTypes.matches(s.getEvents(), eventType)
                    || deliveries.existsBySubscriptionIdAndEventKey(s.getId(), eventKey)) {
                continue;
            }
            deliveries.save(newDelivery(s.getId(), eventType, eventKey, data));
            created++;
        }
        return created;
    }

    /** One delivery for one subscription regardless of its event list (the "send test" action). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long enqueueFor(Long subscriptionId, String eventType, String eventKey, Map<String, Object> data) {
        return deliveries.save(newDelivery(subscriptionId, eventType, eventKey, data)).getId();
    }

    private static WebhookDelivery newDelivery(Long subscriptionId, String eventType, String eventKey,
                                               Map<String, Object> data) {
        WebhookDelivery d = new WebhookDelivery();
        d.setSubscriptionId(subscriptionId);
        d.setEventType(eventType);
        d.setEventKey(eventKey);
        d.setPayload(data);
        d.setStatus(WebhookDelivery.PENDING);
        d.setNextRetryAt(Instant.now());
        return d;
    }
}
