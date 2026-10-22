package com.mfg.ims.crm.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "crm_leads", schema = "ims")
public class CrmLead extends BaseEntity {

    @Column(name = "lead_number", nullable = false, unique = true, length = 20)
    private String leadNumber;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(length = 30)
    private String source;

    @Column(nullable = false, length = 15)
    private String status = LeadStatus.NEW.name();

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "estimated_value", precision = 18, scale = 4)
    private BigDecimal estimatedValue;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @Column(name = "converted_customer_id")
    private Long convertedCustomerId;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "disqualify_reason", length = 300)
    private String disqualifyReason;

    @Column(columnDefinition = "text")
    private String notes;

    public String getLeadNumber() { return leadNumber; }
    public String getCompanyName() { return companyName; }
    public Long getContactId() { return contactId; }
    public String getSource() { return source; }
    public String getStatus() { return status; }
    public Long getOwnerId() { return ownerId; }
    public BigDecimal getEstimatedValue() { return estimatedValue; }
    public LocalDate getExpectedCloseDate() { return expectedCloseDate; }
    public Long getConvertedCustomerId() { return convertedCustomerId; }
    public Instant getConvertedAt() { return convertedAt; }
    public String getDisqualifyReason() { return disqualifyReason; }
    public String getNotes() { return notes; }

    public void setLeadNumber(String v) { this.leadNumber = v; }
    public void setCompanyName(String v) { this.companyName = v; }
    public void setContactId(Long v) { this.contactId = v; }
    public void setSource(String v) { this.source = v; }
    public void setStatus(String v) { this.status = v; }
    public void setOwnerId(Long v) { this.ownerId = v; }
    public void setEstimatedValue(BigDecimal v) { this.estimatedValue = v; }
    public void setExpectedCloseDate(LocalDate v) { this.expectedCloseDate = v; }
    public void setConvertedCustomerId(Long v) { this.convertedCustomerId = v; }
    public void setConvertedAt(Instant v) { this.convertedAt = v; }
    public void setDisqualifyReason(String v) { this.disqualifyReason = v; }
    public void setNotes(String v) { this.notes = v; }
}
