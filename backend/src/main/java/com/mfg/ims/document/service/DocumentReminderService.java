package com.mfg.ims.document.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.document.domain.DocumentMention;
import com.mfg.ims.document.domain.DocumentReminder;
import com.mfg.ims.document.dto.DocumentDtos.CreateMentionRequest;
import com.mfg.ims.document.dto.DocumentDtos.CreateReminderRequest;
import com.mfg.ims.document.dto.DocumentDtos.MentionDto;
import com.mfg.ims.document.dto.DocumentDtos.ReminderDto;
import com.mfg.ims.document.repository.DocumentMentionRepository;
import com.mfg.ims.document.repository.DocumentReminderRepository;

@Service
@Transactional
public class DocumentReminderService {

    private final DocumentReminderRepository reminderRepo;
    private final DocumentMentionRepository mentionRepo;

    public DocumentReminderService(DocumentReminderRepository reminderRepo,
                                    DocumentMentionRepository mentionRepo) {
        this.reminderRepo = reminderRepo;
        this.mentionRepo = mentionRepo;
    }

    @Transactional(readOnly = true)
    public List<ReminderDto> listReminders(String entityType, Long entityId) {
        return reminderRepo.findByEntityTypeAndEntityIdOrderByRemindAtAsc(entityType, entityId)
                .stream().map(DocumentReminderService::toReminderDto).toList();
    }

    public ReminderDto createReminder(CreateReminderRequest body, Long userId) {
        DocumentReminder r = new DocumentReminder();
        r.setEntityType(body.entityType());
        r.setEntityId(body.entityId());
        r.setRemindAt(body.remindAt());
        r.setUserId(userId);
        r.setNote(body.note());
        return toReminderDto(reminderRepo.save(r));
    }

    public ReminderDto dismissReminder(Long id) {
        DocumentReminder r = reminderRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Reminder not found: " + id));
        r.setDismissed(true);
        return toReminderDto(reminderRepo.save(r));
    }

    @Transactional(readOnly = true)
    public List<ReminderDto> myUpcoming(Long userId) {
        Instant until = Instant.now().plusSeconds(24L * 60 * 60 * 7); // next 7 days
        return reminderRepo.findByUserIdAndDismissedFalseAndRemindAtBeforeOrderByRemindAtAsc(userId, until)
                .stream().map(DocumentReminderService::toReminderDto).toList();
    }

    public MentionDto createMention(CreateMentionRequest body, Long mentionedById) {
        DocumentMention m = new DocumentMention();
        m.setEntityType(body.entityType());
        m.setEntityId(body.entityId());
        m.setMentionedUserId(body.mentionedUserId());
        m.setMentionedById(mentionedById);
        m.setNote(body.note());
        return toMentionDto(mentionRepo.save(m));
    }

    private static ReminderDto toReminderDto(DocumentReminder r) {
        return new ReminderDto(r.getId(), r.getEntityType(), r.getEntityId(),
                r.getRemindAt(), r.getUserId(), r.getNote(), r.isDismissed(), r.getCreatedAt());
    }

    private static MentionDto toMentionDto(DocumentMention m) {
        return new MentionDto(m.getId(), m.getEntityType(), m.getEntityId(),
                m.getMentionedUserId(), m.getMentionedById(), m.getNote(), m.getCreatedAt());
    }
}
