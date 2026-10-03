package com.springmfg.ims.crm.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "crm_quotations", schema = "ims")
public class CrmQuotation extends BaseEntity {

    @Column(name = "quotation_number", nullable = false, unique = true, length = 20)
    private String quotationNumber;

    @Column(name = "opportunity_id")
    private Long opportunityId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(nullable = false, length = 15)
    private String status = QuotationStatus.DRAFT.name();

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "payment_terms", length = 60)
    private String paymentTerms;

    @Column(length = 500)
    private String notes;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "reject_reason", length = 300)
    private String rejectReason;

    @Column(name = "converted_sales_order_id")
    private Long convertedSalesOrderId;

    @Column(name = "converted_at")
    private Instant convertedAt;

    public String getQuotationNumber() { return quotationNumber; }
    public Long getOpportunityId() { return opportunityId; }
    public Long getCustomerId() { return customerId; }
    public LocalDate getValidUntil() { return validUntil; }
    public String getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getPaymentTerms() { return paymentTerms; }
    public String getNotes() { return notes; }
    public Instant getSentAt() { return sentAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public String getRejectReason() { return rejectReason; }
    public Long getConvertedSalesOrderId() { return convertedSalesOrderId; }
    public Instant getConvertedAt() { return convertedAt; }

    public void setQuotationNumber(String v) { this.quotationNumber = v; }
    public void setOpportunityId(Long v) { this.opportunityId = v; }
    public void setCustomerId(Long v) { this.customerId = v; }
    public void setValidUntil(LocalDate v) { this.validUntil = v; }
    public void setStatus(String v) { this.status = v; }
    public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public void setPaymentTerms(String v) { this.paymentTerms = v; }
    public void setNotes(String v) { this.notes = v; }
    public void setSentAt(Instant v) { this.sentAt = v; }
    public void setRespondedAt(Instant v) { this.respondedAt = v; }
    public void setRejectReason(String v) { this.rejectReason = v; }
    public void setConvertedSalesOrderId(Long v) { this.convertedSalesOrderId = v; }
    public void setConvertedAt(Instant v) { this.convertedAt = v; }
}
