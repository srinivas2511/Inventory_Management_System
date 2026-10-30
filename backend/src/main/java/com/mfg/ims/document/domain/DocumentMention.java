package com.mfg.ims.document.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_mentions", schema = "ims")
public class DocumentMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "mentioned_user_id", nullable = false)
    private Long mentionedUserId;

    @Column(name = "mentioned_by_id", nullable = false)
    private Long mentionedById;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DocumentMention() {}

    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String v) { this.entityType = v; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long v) { this.entityId = v; }
    public Long getMentionedUserId() { return mentionedUserId; }
    public void setMentionedUserId(Long v) { this.mentionedUserId = v; }
    public Long getMentionedById() { return mentionedById; }
    public void setMentionedById(Long v) { this.mentionedById = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public Instant getCreatedAt() { return createdAt; }
}
