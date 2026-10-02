package com.springmfg.ims.inventory.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alerts", schema = "ims")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_type", nullable = false, length = 30)
    private String alertType;

    @Column(nullable = false, length = 10)
    private String severity = "MEDIUM";

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "open_key", nullable = false, length = 120)
    private String openKey;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Column(nullable = false, length = 15)
    private String status = "OPEN";

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolution_note", length = 300)
    private String resolutionNote;

    public Alert() {}

    public Long getId() { return id; }
    public String getAlertType() { return alertType; }
    public String getSeverity() { return severity; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getOpenKey() { return openKey; }
    public String getMessage() { return message; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Long getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Long getResolvedBy() { return resolvedBy; }
    public String getResolutionNote() { return resolutionNote; }
    public String getStatus() { return status; }

    public void setAlertType(String alertType) { this.alertType = alertType; }
    public void setSeverity(String severity) { this.severity = severity; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public void setOpenKey(String openKey) { this.openKey = openKey; }
    public void setMessage(String message) { this.message = message; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public void setAcknowledgedBy(Long acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public void setStatus(String status) { this.status = status; }

    public boolean isOpen() { return resolvedAt == null; }
}
