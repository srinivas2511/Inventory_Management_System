package com.mfg.ims.approval.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "approval_requests", schema = "ims")
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    @Column(name = "required_permission", nullable = false, length = 60)
    private String requiredPermission;

    @Column(nullable = false, length = 15)
    private String status = "PENDING";

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ApprovalRequest() {}

    public Long getId() { return id; }
    public String getDocumentType() { return documentType; }
    public Long getDocumentId() { return documentId; }
    public Integer getStepNo() { return stepNo; }
    public String getRequiredPermission() { return requiredPermission; }
    public String getStatus() { return status; }
    public Long getRequestedBy() { return requestedBy; }
    public Long getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    public void setRequiredPermission(String requiredPermission) { this.requiredPermission = requiredPermission; }
    public void setStatus(String status) { this.status = status; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }
    public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public void setComment(String comment) { this.comment = comment; }

    public boolean isPending() { return "PENDING".equals(status); }
    public boolean isApproved() { return "APPROVED".equals(status); }
}
