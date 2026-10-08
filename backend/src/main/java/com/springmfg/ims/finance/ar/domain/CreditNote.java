package com.springmfg.ims.finance.ar.domain;

import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A credit against an AR invoice: reduces what the customer owes and, optionally, receives returned goods. */
@Entity
@Table(name = "credit_notes", schema = "ims")
public class CreditNote extends BaseEntity {

    @Column(name = "credit_note_number", nullable = false, unique = true, length = 25)
    private String creditNoteNumber;

    @Column(name = "ar_invoice_id", nullable = false)
    private Long arInvoiceId;

    @Column(name = "credit_date", nullable = false)
    private LocalDate creditDate;

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

    @Column(name = "return_fg_batch_id")
    private Long returnFgBatchId;

    @Column(name = "return_quantity", precision = 18, scale = 3)
    private BigDecimal returnQuantity;

    @Column(name = "return_warehouse_id")
    private Long returnWarehouseId;

    @Column(name = "return_location_id")
    private Long returnLocationId;

    @Column(name = "return_transaction_number", length = 30)
    private String returnTransactionNumber;

    public String getCreditNoteNumber() { return creditNoteNumber; }
    public Long getArInvoiceId() { return arInvoiceId; }
    public LocalDate getCreditDate() { return creditDate; }
    public String getReason() { return reason; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTaxTotal() { return taxTotal; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getCarryingInr() { return carryingInr; }
    public String getStatus() { return status; }
    public Long getJournalHeaderId() { return journalHeaderId; }
    public Long getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public Long getReturnFgBatchId() { return returnFgBatchId; }
    public BigDecimal getReturnQuantity() { return returnQuantity; }
    public Long getReturnWarehouseId() { return returnWarehouseId; }
    public Long getReturnLocationId() { return returnLocationId; }
    public String getReturnTransactionNumber() { return returnTransactionNumber; }

    public void setCreditNoteNumber(String v) { this.creditNoteNumber = v; }
    public void setArInvoiceId(Long v) { this.arInvoiceId = v; }
    public void setCreditDate(LocalDate v) { this.creditDate = v; }
    public void setReason(String v) { this.reason = v; }
    public void setSubtotal(BigDecimal v) { this.subtotal = v; }
    public void setTaxTotal(BigDecimal v) { this.taxTotal = v; }
    public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public void setCarryingInr(BigDecimal v) { this.carryingInr = v; }
    public void setStatus(String v) { this.status = v; }
    public void setJournalHeaderId(Long v) { this.journalHeaderId = v; }
    public void setApprovedBy(Long v) { this.approvedBy = v; }
    public void setApprovedAt(Instant v) { this.approvedAt = v; }
    public void setReturnFgBatchId(Long v) { this.returnFgBatchId = v; }
    public void setReturnQuantity(BigDecimal v) { this.returnQuantity = v; }
    public void setReturnWarehouseId(Long v) { this.returnWarehouseId = v; }
    public void setReturnLocationId(Long v) { this.returnLocationId = v; }
    public void setReturnTransactionNumber(String v) { this.returnTransactionNumber = v; }
}
