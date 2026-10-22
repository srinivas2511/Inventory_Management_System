package com.mfg.ims.qualityplus.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Corrective and preventive action (QMS-009). */
@Entity
@Table(name = "capa_records", schema = "ims")
public class CapaRecord extends BaseEntity {

    @Column(name = "capa_number", nullable = false, length = 20, unique = true)
    private String capaNumber;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private CapaSourceType sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "root_cause")
    private String rootCause;

    @Column(name = "corrective_action")
    private String correctiveAction;

    @Column(name = "preventive_action")
    private String preventiveAction;

    @Column(name = "verification_notes")
    private String verificationNotes;

    @Column(name = "target_completion_date")
    private LocalDate targetCompletionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CapaStatus status = CapaStatus.OPEN;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    public String getCapaNumber() { return capaNumber; }
    public void setCapaNumber(String v) { this.capaNumber = v; }

    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public CapaSourceType getSourceType() { return sourceType; }
    public void setSourceType(CapaSourceType v) { this.sourceType = v; }

    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long v) { this.sourceId = v; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String v) { this.rootCause = v; }

    public String getCorrectiveAction() { return correctiveAction; }
    public void setCorrectiveAction(String v) { this.correctiveAction = v; }

    public String getPreventiveAction() { return preventiveAction; }
    public void setPreventiveAction(String v) { this.preventiveAction = v; }

    public String getVerificationNotes() { return verificationNotes; }
    public void setVerificationNotes(String v) { this.verificationNotes = v; }

    public LocalDate getTargetCompletionDate() { return targetCompletionDate; }
    public void setTargetCompletionDate(LocalDate v) { this.targetCompletionDate = v; }

    public CapaStatus getStatus() { return status; }
    public void setStatus(CapaStatus v) { this.status = v; }

    public Long getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Long v) { this.assignedTo = v; }

    public Long getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(Long v) { this.verifiedBy = v; }

    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant v) { this.openedAt = v; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant v) { this.closedAt = v; }
}
