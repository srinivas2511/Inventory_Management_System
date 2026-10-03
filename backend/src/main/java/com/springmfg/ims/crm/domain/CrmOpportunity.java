package com.springmfg.ims.crm.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "crm_opportunities", schema = "ims")
public class CrmOpportunity extends BaseEntity {

    @Column(name = "opportunity_number", nullable = false, unique = true, length = 20)
    private String opportunityNumber;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, length = 20)
    private String stage = OpportunityStage.PROSPECT.name();

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "probability_pct", nullable = false)
    private Integer probabilityPct = OpportunityStage.PROSPECT.defaultProbability();

    @Column(name = "estimated_value", precision = 18, scale = 4)
    private BigDecimal estimatedValue;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "converted_quotation_id")
    private Long convertedQuotationId;

    @Column(name = "won_at")
    private Instant wonAt;

    @Column(name = "lost_reason", length = 200)
    private String lostReason;

    public String getOpportunityNumber() { return opportunityNumber; }
    public Long getLeadId() { return leadId; }
    public Long getCustomerId() { return customerId; }
    public Long getContactId() { return contactId; }
    public String getDescription() { return description; }
    public String getStage() { return stage; }
    public Integer getProbabilityPct() { return probabilityPct; }
    public BigDecimal getEstimatedValue() { return estimatedValue; }
    public LocalDate getExpectedCloseDate() { return expectedCloseDate; }
    public Long getOwnerId() { return ownerId; }
    public Long getConvertedQuotationId() { return convertedQuotationId; }
    public Instant getWonAt() { return wonAt; }
    public String getLostReason() { return lostReason; }

    public void setOpportunityNumber(String v) { this.opportunityNumber = v; }
    public void setLeadId(Long v) { this.leadId = v; }
    public void setCustomerId(Long v) { this.customerId = v; }
    public void setContactId(Long v) { this.contactId = v; }
    public void setDescription(String v) { this.description = v; }
    public void setStage(String v) { this.stage = v; }
    public void setProbabilityPct(Integer v) { this.probabilityPct = v; }
    public void setEstimatedValue(BigDecimal v) { this.estimatedValue = v; }
    public void setExpectedCloseDate(LocalDate v) { this.expectedCloseDate = v; }
    public void setOwnerId(Long v) { this.ownerId = v; }
    public void setConvertedQuotationId(Long v) { this.convertedQuotationId = v; }
    public void setWonAt(Instant v) { this.wonAt = v; }
    public void setLostReason(String v) { this.lostReason = v; }
}
