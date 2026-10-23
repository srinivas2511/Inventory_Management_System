package com.mfg.ims.finance.ap.domain;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A price-adjustment debit against an AP invoice: reduces what we owe the supplier. */
@Entity
@Table(name = "ap_debit_notes", schema = "ims")
public class ApDebitNote extends BaseEntity {

    @Column(name = "debit_note_number", nullable = false, unique = true, length = 25)
    private String debitNoteNumber;

    @Column(name = "ap_invoice_id", nullable = false)
    private Long apInvoiceId;

    @Column(name = "debit_date", nullable = false)
    private LocalDate debitDate;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "tax_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "carrying_inr", nullable = false, precision = 18, scale = 4)
    private BigDecimal carryingInr = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    private String status = "DRAFT";

    @Column(name = "journal_header_id")
    private Long journalHeaderId;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    public String getDebitNoteNumber() { return debitNoteNumber; }
    public Long getApInvoiceId() { return apInvoiceId; }
    public LocalDate getDebitDate() { return debitDate; }
    public String getReason() { return reason; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTaxTotal() { return taxTotal; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getCarryingInr() { return carryingInr; }
    public String getStatus() { return status; }
    public Long getJournalHeaderId() { return journalHeaderId; }
    public Long getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }

    public void setDebitNoteNumber(String v) { this.debitNoteNumber = v; }
    public void setApInvoiceId(Long v) { this.apInvoiceId = v; }
    public void setDebitDate(LocalDate v) { this.debitDate = v; }
    public void setReason(String v) { this.reason = v; }
    public void setSubtotal(BigDecimal v) { this.subtotal = v; }
    public void setTaxTotal(BigDecimal v) { this.taxTotal = v; }
    public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public void setCarryingInr(BigDecimal v) { this.carryingInr = v; }
    public void setStatus(String v) { this.status = v; }
    public void setJournalHeaderId(Long v) { this.journalHeaderId = v; }
    public void setApprovedBy(Long v) { this.approvedBy = v; }
    public void setApprovedAt(Instant v) { this.approvedAt = v; }
}
