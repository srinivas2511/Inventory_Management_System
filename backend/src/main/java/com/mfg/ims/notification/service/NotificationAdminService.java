package com.mfg.ims.notification.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.notification.domain.NotificationChannel;
import com.mfg.ims.notification.domain.NotificationStatus;
import com.mfg.ims.notification.domain.NotificationTemplate;
import com.mfg.ims.notification.dto.NotificationDtos.DeliveryLogDto;
import com.mfg.ims.notification.dto.NotificationDtos.SaveTemplateRequest;
import com.mfg.ims.notification.dto.NotificationDtos.TemplateDto;
import com.mfg.ims.notification.repository.NotificationRepository;
import com.mfg.ims.notification.repository.NotificationTemplateRepository;

/** Template maintenance and the delivery log, for administrators holding NOTIFICATION_MANAGE. */
@Service
public class NotificationAdminService {

    private final NotificationTemplateRepository templates;
    private final NotificationRepository notifications;
    private final AuditService audit;

    public NotificationAdminService(NotificationTemplateRepository templates, NotificationRepository notifications,
                                    AuditService audit) {
        this.templates = templates;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TemplateDto> templates() {
        return templates.findAllByOrderByTemplateCodeAsc().stream().map(NotificationAdminService::toDto).toList();
    }

    @Transactional
    public TemplateDto updateTemplate(Long id, SaveTemplateRequest req) {
        NotificationTemplate t = templates.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification template", id));
        String before = t.getChannel() + "|" + t.isActive();
        t.setChannel(req.channel());
        t.setDescription(req.description());
        t.setSubjectTemplate(req.subjectTemplate());
        t.setBodyTemplate(req.bodyTemplate());
        if (req.active() != null) {
            t.setActive(req.active());
        }
        t = templates.save(t);
        audit.record("NOTIFICATION_TEMPLATE_UPDATED", "NotificationTemplate", String.valueOf(id), before,
                t.getChannel() + "|" + t.isActive(), t.getTemplateCode());
        return toDto(t);
    }

    @Transactional(readOnly = true)
    public Page<DeliveryLogDto> log(NotificationStatus status, NotificationChannel channel, Pageable pageable) {
        return notifications.searchLog(status, channel, pageable)
                .map(n -> new DeliveryLogDto(n.getId(), n.getUserId(), n.getRecipientEmail(), n.getTemplateCode(),
                        n.getChannel(), n.getSubject(), n.getStatus(), n.getAttempts(), n.getNextAttemptAt(),
                        n.getLastError(), n.getCreatedAt(), n.getSentAt()));
    }

    private static TemplateDto toDto(NotificationTemplate t) {
        return new TemplateDto(t.getId(), t.getTemplateCode(), t.getChannel(), t.getDescription(),
                t.getSubjectTemplate(), t.getBodyTemplate(), t.isActive());
    }
}
