package com.mfg.ims.finance.advance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "advance_receipts", schema = "ims")
public class AdvanceReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "notes")
    private String notes;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "applied_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal appliedAmount = BigDecimal.ZERO;

    @Column(name = "journal_id")
    private Long journalId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long v) { this.customerId = v; }
    public LocalDate getReceiptDate() { return receiptDate; }
    public void setReceiptDate(LocalDate v) { this.receiptDate = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String v) { this.currencyCode = v; }
    public String getReference() { return reference; }
    public void setReference(String v) { this.reference = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public BigDecimal getAppliedAmount() { return appliedAmount; }
    public void setAppliedAmount(BigDecimal v) { this.appliedAmount = v; }
    public Long getJournalId() { return journalId; }
    public void setJournalId(Long v) { this.journalId = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long v) { this.createdBy = v; }
}
