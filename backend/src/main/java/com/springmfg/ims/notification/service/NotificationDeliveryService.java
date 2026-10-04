package com.springmfg.ims.notification.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.iam.repository.UserRepository;
import com.springmfg.ims.notification.domain.Notification;
import com.springmfg.ims.notification.domain.NotificationChannel;
import com.springmfg.ims.notification.domain.NotificationStatus;
import com.springmfg.ims.notification.repository.NotificationRepository;

/**
 * Sends one queued e-mail. Each delivery is its own transaction holding a row lock, so two workers can never send the
 * same message, and the outcome (SENT, a retry time, or FAILED) is committed whether or not SMTP answered. Delivery is
 * at-least-once: if the process dies between the SMTP accept and the commit, the message is sent again on restart.
 */
@Service
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    public enum Outcome { SENT, RETRY_SCHEDULED, FAILED, SKIPPED }

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final EmailSender sender;
    private final SystemSettingService settings;

    public NotificationDeliveryService(NotificationRepository notifications, UserRepository users,
                                       EmailSender sender, SystemSettingService settings) {
        this.notifications = notifications;
        this.users = users;
        this.sender = sender;
        this.settings = settings;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome deliver(Long notificationId) {
        Notification n = notifications.findByIdForUpdate(notificationId).orElse(null);
        if (n == null || n.getChannel() != NotificationChannel.EMAIL || n.getStatus() != NotificationStatus.PENDING) {
            return Outcome.SKIPPED;
        }
        Instant now = Instant.now();
        if (n.getNextAttemptAt() != null && n.getNextAttemptAt().isAfter(now)) {
            return Outcome.SKIPPED;
        }
        n.setAttempts(n.getAttempts() + 1);
        try {
            String address = n.getRecipientEmail() != null ? n.getRecipientEmail()
                    : users.findById(n.getUserId()).map(u -> u.getEmail()).orElse(null);
            if (address == null || address.isBlank()) {
                throw new IllegalStateException("Recipient has no e-mail address");
            }
            sender.send(address, n.getSubject(), n.getBody());
            n.setStatus(NotificationStatus.SENT);
            n.setSentAt(now);
            n.setNextAttemptAt(null);
            n.setLastError(null);
            return Outcome.SENT;
        } catch (RuntimeException e) {
            n.setLastError(describe(e));
            int max = Math.max(1, settings.getInt("notification.email.max_attempts", 5));
            if (n.getAttempts() >= max) {
                n.setStatus(NotificationStatus.FAILED);
                n.setNextAttemptAt(null);
                log.warn("Notification {} failed permanently after {} attempts: {}", n.getId(), n.getAttempts(),
                        n.getLastError());
                return Outcome.FAILED;
            }
            n.setNextAttemptAt(now.plus(backoff(n.getAttempts())));
            log.info("Notification {} attempt {} failed, retrying at {}: {}", n.getId(), n.getAttempts(),
                    n.getNextAttemptAt(), n.getLastError());
            return Outcome.RETRY_SCHEDULED;
        }
    }

    /** First retry after the configured base delay, doubling on every further attempt, capped at one hour. */
    Duration backoff(int attemptsSoFar) {
        long base = Math.max(1, settings.getInt("notification.email.retry_base_seconds", 60));
        long factor = 1L << Math.min(Math.max(attemptsSoFar - 1, 0), 20);
        Duration d = Duration.ofSeconds(base * factor);
        return d.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : d;
    }

    /** Exception class and first line of its message, bounded; never the stack trace or connection details. */
    static String describe(Throwable e) {
        String message = e.getMessage();
        String first = message == null ? "" : message.lines().findFirst().orElse("");
        String text = e.getClass().getSimpleName() + (first.isBlank() ? "" : ": " + first);
        return text.length() <= 300 ? text : text.substring(0, 300);
    }
}
