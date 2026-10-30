package com.mfg.ims.document.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.document.domain.DocumentReminder;

public interface DocumentReminderRepository extends JpaRepository<DocumentReminder, Long> {

    List<DocumentReminder> findByEntityTypeAndEntityIdOrderByRemindAtAsc(String entityType, Long entityId);

    List<DocumentReminder> findByUserIdAndDismissedFalseAndRemindAtBeforeOrderByRemindAtAsc(
            Long userId, Instant before);
}
