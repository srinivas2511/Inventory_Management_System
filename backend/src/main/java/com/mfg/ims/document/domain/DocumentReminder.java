package com.mfg.ims.document.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_reminders", schema = "ims")
public class DocumentReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "remind_at", nullable = false)
    private Instant remindAt;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false)
    private boolean dismissed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DocumentReminder() {}

    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String v) { this.entityType = v; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long v) { this.entityId = v; }
    public Instant getRemindAt() { return remindAt; }
    public void setRemindAt(Instant v) { this.remindAt = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public boolean isDismissed() { return dismissed; }
    public void setDismissed(boolean v) { this.dismissed = v; }
    public Instant getCreatedAt() { return createdAt; }
}
