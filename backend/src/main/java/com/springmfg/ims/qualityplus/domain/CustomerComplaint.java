package com.springmfg.ims.qualityplus.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A customer complaint, optionally tied to an FG batch and a CAPA (QMS-009). */
@Entity
@Table(name = "customer_complaints", schema = "ims")
public class CustomerComplaint extends BaseEntity {

    @Column(name = "complaint_number", nullable = false, length = 20, unique = true)
    private String complaintNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "fg_batch_id")
    private Long fgBatchId;

    @Column(name = "complaint_date", nullable = false)
    private LocalDate complaintDate;

    @Column(name = "received_by", nullable = false)
    private Long receivedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private ComplaintCategory category = ComplaintCategory.OTHER;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "customer_ref", length = 60)
    private String customerRef;

    @Column(name = "quantity_complained")
    private BigDecimal quantityComplained;

    @Column(name = "quantity_returned")
    private BigDecimal quantityReturned;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposition", length = 15)
    private ComplaintDisposition disposition;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ComplaintStatus status = ComplaintStatus.OPEN;

    @Column(name = "capa_id")
    private Long capaId;

    @Column(name = "closed_at")
    private Instant closedAt;

    public String getComplaintNumber() { return complaintNumber; }
    public void setComplaintNumber(String v) { this.complaintNumber = v; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long v) { this.customerId = v; }

    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }

    public Long getFgBatchId() { return fgBatchId; }
    public void setFgBatchId(Long v) { this.fgBatchId = v; }

    public LocalDate getComplaintDate() { return complaintDate; }
    public void setComplaintDate(LocalDate v) { this.complaintDate = v; }

    public Long getReceivedBy() { return receivedBy; }
    public void setReceivedBy(Long v) { this.receivedBy = v; }

    public ComplaintCategory getCategory() { return category; }
    public void setCategory(ComplaintCategory v) { this.category = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public String getCustomerRef() { return customerRef; }
    public void setCustomerRef(String v) { this.customerRef = v; }

    public BigDecimal getQuantityComplained() { return quantityComplained; }
    public void setQuantityComplained(BigDecimal v) { this.quantityComplained = v; }

    public BigDecimal getQuantityReturned() { return quantityReturned; }
    public void setQuantityReturned(BigDecimal v) { this.quantityReturned = v; }

    public ComplaintDisposition getDisposition() { return disposition; }
    public void setDisposition(ComplaintDisposition v) { this.disposition = v; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String v) { this.resolutionNotes = v; }

    public ComplaintStatus getStatus() { return status; }
    public void setStatus(ComplaintStatus v) { this.status = v; }

    public Long getCapaId() { return capaId; }
    public void setCapaId(Long v) { this.capaId = v; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant v) { this.closedAt = v; }
}
