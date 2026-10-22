package com.mfg.ims.notification.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.iam.domain.User;
import com.mfg.ims.iam.repository.UserRepository;
import com.mfg.ims.notification.domain.Notification;
import com.mfg.ims.notification.domain.NotificationChannel;
import com.mfg.ims.notification.domain.NotificationStatus;
import com.mfg.ims.notification.domain.NotificationSubscription;
import com.mfg.ims.notification.domain.NotificationTemplate;
import com.mfg.ims.notification.repository.NotificationRepository;
import com.mfg.ims.notification.repository.NotificationSubscriptionRepository;
import com.mfg.ims.notification.repository.NotificationTemplateRepository;

/**
 * Creates notifications from a template. It runs in its own transaction (REQUIRES_NEW): callers are after-commit
 * event listeners, where joining the finished transaction would silently lose the rows, and a notification must
 * never be able to roll back, or block, the business transaction that caused it. E-mail is only queued here; the
 * outbox job sends it.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /**
     * @param userId       recipient user, or null for an external address
     * @param email        external recipient address (used when userId is null)
     * @param onlyChannels restricts the channels further than the template does; null = no restriction
     * @param dedupKey     optional idempotency key; the same key never creates a second row per recipient and channel
     */
    public record Request(String templateCode, Long userId, String email, Map<String, Object> payload,
                          String entityType, Long entityId, String dedupKey, Set<NotificationChannel> onlyChannels) {

        public static Request toUser(String templateCode, Long userId, Map<String, Object> payload,
                                     String entityType, Long entityId, String dedupKey) {
            return new Request(templateCode, userId, null, payload, entityType, entityId, dedupKey, null);
        }

        public static Request toEmail(String templateCode, String email, Map<String, Object> payload,
                                      String entityType, Long entityId, String dedupKey) {
            return new Request(templateCode, null, email, payload, entityType, entityId, dedupKey, null);
        }

        public Request restrictedTo(Set<NotificationChannel> channels) {
            return new Request(templateCode, userId, email, payload, entityType, entityId, dedupKey, channels);
        }

        Request forUser(Long id) {
            return new Request(templateCode, id, null, payload, entityType, entityId, dedupKey, onlyChannels);
        }
    }

    private final NotificationTemplateRepository templates;
    private final NotificationSubscriptionRepository subscriptions;
    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationService(NotificationTemplateRepository templates,
                               NotificationSubscriptionRepository subscriptions,
                               NotificationRepository notifications, UserRepository users) {
        this.templates = templates;
        this.subscriptions = subscriptions;
        this.notifications = notifications;
        this.users = users;
    }

    /** Returns how many notification rows were created. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int send(Request request) {
        return create(request);
    }

    /** Sends the same message to every active user holding {@code authority}. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int sendToPermissionHolders(String authority, Request prototype) {
        int created = 0;
        for (Long userId : users.findActiveIdsByPermission(authority)) {
            created += create(prototype.forUser(userId));
        }
        return created;
    }

    private int create(Request r) {
        NotificationTemplate template = templates.findByTemplateCode(r.templateCode()).filter(
                NotificationTemplate::isActive).orElse(null);
        if (template == null) {
            log.debug("No active notification template {}", r.templateCode());
            return 0;
        }
        User user = null;
        if (r.userId() != null) {
            user = users.findById(r.userId()).filter(User::isActive).orElse(null);
            if (user == null) {
                return 0;
            }
        }
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        channels.addAll(template.channels());
        if (r.onlyChannels() != null) {
            channels.retainAll(r.onlyChannels());
        }
        Map<NotificationChannel, Boolean> preference = new HashMap<>();
        if (user != null) {
            for (NotificationSubscription s : subscriptions.findByUserIdAndTemplateCode(user.getId(),
                    template.getTemplateCode())) {
                preference.put(s.getChannel(), s.isEnabled());
            }
        }
        Map<String, Object> payload = r.payload() == null ? Map.of() : r.payload();
        int created = 0;
        for (NotificationChannel channel : channels) {
            if (user != null && !preference.getOrDefault(channel, true)) {
                continue;
            }
            String address = null;
            if (channel == NotificationChannel.EMAIL) {
                address = user != null ? user.getEmail() : r.email();
                if (address == null || address.isBlank()) {
                    continue;
                }
            } else if (user == null) {
                continue;
            }
            String dedup = null;
            if (r.dedupKey() != null) {
                dedup = r.dedupKey() + ":" + channel + ":"
                        + (user != null ? user.getId() : address.trim().toLowerCase(Locale.ROOT));
                if (notifications.existsByDedupKey(dedup)) {
                    continue;
                }
            }
            Notification n = new Notification();
            n.setUserId(user == null ? null : user.getId());
            n.setRecipientEmail(user == null ? address.trim() : null);
            n.setTemplateCode(template.getTemplateCode());
            n.setChannel(channel);
            n.setSubject(truncate(TemplateRenderer.renderLine(template.getSubjectTemplate(), payload), 200));
            n.setBody(TemplateRenderer.render(template.getBodyTemplate(), payload));
            n.setEntityType(r.entityType());
            n.setEntityId(r.entityId());
            n.setDedupKey(dedup);
            if (channel == NotificationChannel.IN_APP) {
                n.setStatus(NotificationStatus.SENT);
                n.setSentAt(Instant.now());
            } else {
                n.setStatus(NotificationStatus.PENDING);
                n.setNextAttemptAt(Instant.now());
            }
            notifications.save(n);
            created++;
        }
        return created;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Convenience for listeners that need an in-app only restriction. */
    public static Set<NotificationChannel> inAppOnly() {
        return Set.copyOf(List.of(NotificationChannel.IN_APP));
    }
}
