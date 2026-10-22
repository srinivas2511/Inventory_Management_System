package com.mfg.ims.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.notification.domain.Notification;
import com.mfg.ims.notification.domain.NotificationChannel;
import com.mfg.ims.notification.domain.NotificationStatus;
import com.mfg.ims.notification.domain.NotificationSubscription;
import com.mfg.ims.notification.domain.NotificationTemplate;
import com.mfg.ims.notification.dto.NotificationDtos.ChannelPreferenceDto;
import com.mfg.ims.notification.dto.NotificationDtos.NotificationDto;
import com.mfg.ims.notification.dto.NotificationDtos.PreferenceDto;
import com.mfg.ims.notification.dto.NotificationDtos.SetPreferenceRequest;
import com.mfg.ims.notification.dto.NotificationDtos.UnreadCountDto;
import com.mfg.ims.notification.repository.NotificationRepository;
import com.mfg.ims.notification.repository.NotificationSubscriptionRepository;
import com.mfg.ims.notification.repository.NotificationTemplateRepository;

/** A user's own in-app inbox and channel preferences. Everything here is scoped to the signed-in user. */
@Service
public class NotificationInboxService {

    private final NotificationRepository notifications;
    private final NotificationTemplateRepository templates;
    private final NotificationSubscriptionRepository subscriptions;

    public NotificationInboxService(NotificationRepository notifications, NotificationTemplateRepository templates,
                                    NotificationSubscriptionRepository subscriptions) {
        this.notifications = notifications;
        this.templates = templates;
        this.subscriptions = subscriptions;
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> inbox(boolean unreadOnly, Pageable pageable) {
        Long userId = currentUserId();
        Page<Notification> page = unreadOnly
                ? notifications.findByUserIdAndChannelAndReadAtIsNull(userId, NotificationChannel.IN_APP, pageable)
                : notifications.findByUserIdAndChannel(userId, NotificationChannel.IN_APP, pageable);
        return page.map(NotificationInboxService::toDto);
    }

    @Transactional(readOnly = true)
    public UnreadCountDto unreadCount() {
        return new UnreadCountDto(notifications.countByUserIdAndChannelAndReadAtIsNull(currentUserId(),
                NotificationChannel.IN_APP));
    }

    @Transactional
    public NotificationDto markRead(Long id) {
        Notification n = notifications.findByIdAndUserId(id, currentUserId())
                .filter(x -> x.getChannel() == NotificationChannel.IN_APP)
                .orElseThrow(() -> new NotFoundException("Notification", id));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            n.setStatus(NotificationStatus.READ);
            n = notifications.save(n);
        }
        return toDto(n);
    }

    @Transactional
    public UnreadCountDto markAllRead() {
        notifications.markAllRead(currentUserId(), NotificationChannel.IN_APP, NotificationStatus.READ, Instant.now());
        return new UnreadCountDto(0);
    }

    // ---- preferences ----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PreferenceDto> preferences() {
        Map<String, Map<NotificationChannel, Boolean>> overrides = subscriptions.findByUserId(currentUserId()).stream()
                .collect(Collectors.groupingBy(NotificationSubscription::getTemplateCode,
                        Collectors.toMap(NotificationSubscription::getChannel, NotificationSubscription::isEnabled)));
        return templates.findByActiveTrueOrderByTemplateCodeAsc().stream()
                .map(t -> new PreferenceDto(t.getTemplateCode(), t.getDescription(), t.channels().stream()
                        .sorted()
                        .map(c -> new ChannelPreferenceDto(c, overrides
                                .getOrDefault(t.getTemplateCode(), Map.of()).getOrDefault(c, true)))
                        .toList()))
                .toList();
    }

    @Transactional
    public List<PreferenceDto> setPreference(SetPreferenceRequest req) {
        NotificationTemplate template = templates.findByTemplateCode(req.templateCode())
                .filter(NotificationTemplate::isActive)
                .orElseThrow(() -> new NotFoundException("Notification template", req.templateCode()));
        if (!template.channels().contains(req.channel())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Template " + template.getTemplateCode() + " is not sent by " + req.channel());
        }
        Long userId = currentUserId();
        NotificationSubscription s = subscriptions
                .findByUserIdAndTemplateCodeAndChannel(userId, template.getTemplateCode(), req.channel())
                .orElseGet(() -> {
                    NotificationSubscription created = new NotificationSubscription();
                    created.setUserId(userId);
                    created.setTemplateCode(template.getTemplateCode());
                    created.setChannel(req.channel());
                    return created;
                });
        s.setEnabled(req.enabled());
        subscriptions.save(s);
        return preferences();
    }

    private static NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getTemplateCode(), n.getSubject(), n.getBody(), n.getEntityType(),
                n.getEntityId(), n.getCreatedAt(), n.getReadAt(), n.getReadAt() != null);
    }

    private static Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) {
            return u.id();
        }
        throw new IllegalStateException("No authenticated user");
    }
}
