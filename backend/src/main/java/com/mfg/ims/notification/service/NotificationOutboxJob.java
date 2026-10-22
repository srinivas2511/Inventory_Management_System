package com.mfg.ims.notification.service;

import java.time.Instant;
import java.util.List;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mfg.ims.notification.domain.NotificationChannel;
import com.mfg.ims.notification.domain.NotificationStatus;
import com.mfg.ims.notification.repository.NotificationRepository;

/** Drains the e-mail outbox. ShedLock keeps it to one instance at a time; the row lock makes a double run harmless. */
@Component
public class NotificationOutboxJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxJob.class);
    static final int BATCH = 50;
    /** Bounds one run so a persistent database fault cannot spin the job forever. */
    static final int MAX_ROUNDS = 20;

    private final NotificationRepository notifications;
    private final NotificationDeliveryService delivery;

    public NotificationOutboxJob(NotificationRepository notifications, NotificationDeliveryService delivery) {
        this.notifications = notifications;
        this.delivery = delivery;
    }

    @Scheduled(fixedDelayString = "${ims.notification.outbox-delay-ms:30000}",
            initialDelayString = "${ims.notification.outbox-delay-ms:30000}")
    @SchedulerLock(name = "notification_outbox", lockAtLeastFor = "PT5S", lockAtMostFor = "PT5M")
    public void run() {
        try {
            int handled = processDue();
            if (handled > 0) {
                log.debug("Notification outbox handled {} message(s)", handled);
            }
        } catch (RuntimeException e) {
            log.warn("Notification outbox run failed: {}", e.toString());
        }
    }

    /** Delivers every e-mail that is due, one batch at a time; returns how many were handled. */
    public int processDue() {
        int handled = 0;
        int rounds = 0;
        List<Long> ids;
        do {
            ids = notifications.findDueIds(NotificationChannel.EMAIL, NotificationStatus.PENDING, Instant.now(),
                    PageRequest.of(0, BATCH));
            for (Long id : ids) {
                try {
                    if (delivery.deliver(id) != NotificationDeliveryService.Outcome.SKIPPED) {
                        handled++;
                    }
                } catch (RuntimeException e) {
                    log.warn("Delivery of notification {} errored: {}", id, e.toString());
                }
            }
            // A retry is scheduled in the future, so it drops out of the next page; stop when nothing was due.
        } while (ids.size() == BATCH && ++rounds < MAX_ROUNDS);
        return handled;
    }
}
