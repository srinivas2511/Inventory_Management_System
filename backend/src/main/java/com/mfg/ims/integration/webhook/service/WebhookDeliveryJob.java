package com.mfg.ims.integration.webhook.service;

import java.time.Instant;
import java.util.List;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mfg.ims.integration.webhook.repository.WebhookDeliveryRepository;

/** Drains the webhook outbox. ShedLock keeps it to one node; the row lock makes a double run harmless. */
@Component
public class WebhookDeliveryJob {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryJob.class);
    static final int BATCH = 50;
    static final int MAX_ROUNDS = 20;

    private final WebhookDeliveryRepository deliveries;
    private final WebhookDeliveryService service;

    public WebhookDeliveryJob(WebhookDeliveryRepository deliveries, WebhookDeliveryService service) {
        this.deliveries = deliveries;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${ims.integration.webhook.poll-delay-ms:30000}",
            initialDelayString = "${ims.integration.webhook.poll-delay-ms:30000}")
    @SchedulerLock(name = "webhook_delivery", lockAtLeastFor = "PT5S", lockAtMostFor = "PT10M")
    public void run() {
        try {
            int handled = processDue();
            if (handled > 0) {
                log.debug("Webhook outbox handled {} delivery(ies)", handled);
            }
        } catch (RuntimeException e) {
            log.warn("Webhook outbox run failed: {}", e.toString());
        }
    }

    /** Delivers everything that is due, a batch at a time; returns how many deliveries were attempted. */
    public int processDue() {
        int handled = 0;
        int rounds = 0;
        List<Long> ids;
        do {
            ids = deliveries.findDueIds(Instant.now(), PageRequest.of(0, BATCH));
            for (Long id : ids) {
                try {
                    if (service.deliver(id) != WebhookDeliveryService.Outcome.SKIPPED) {
                        handled++;
                    }
                } catch (RuntimeException e) {
                    log.warn("Webhook delivery {} could not be processed: {}", id, e.toString());
                }
            }
            rounds++;
        } while (ids.size() == BATCH && rounds < MAX_ROUNDS);
        return handled;
    }
}
