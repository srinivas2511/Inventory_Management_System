package com.springmfg.ims.finance.ap.domain;

import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ap_invoices", schema = "ims")
public class ApInvoice extends BaseEntity {

    @Column(name = "invoice_number", nullable = false, unique = true, length = 25)
    private String invoiceNumber;

    @Column(name = "supplier_invoice_no", length = 40)
    private String supplierInvoiceNo;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "goods_receipt_id")
    private Long goodsReceiptId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "tax_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 15)
    private String status = "DRAFT";

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(name = "journal_header_id")
    private Long journalHeaderId;

    public String getInvoiceNumber() { return invoiceNumber; }
    public String getSupplierInvoiceNo() { return supplierInvoiceNo; }
    public Long getSupplierId() { return supplierId; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public LocalDate getDueDate() { return dueDate; }
    public Long getGoodsReceiptId() { return goodsReceiptId; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTaxTotal() { return taxTotal; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public String getStatus() { return status; }
    public String getCurrencyCode() { return currencyCode; }
    public Long getPeriodId() { return periodId; }
    public Long getJournalHeaderId() { return journalHeaderId; }

    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public void setSupplierInvoiceNo(String supplierInvoiceNo) { this.supplierInvoiceNo = supplierInvoiceNo; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public void setGoodsReceiptId(Long goodsReceiptId) { this.goodsReceiptId = goodsReceiptId; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public void setTaxTotal(BigDecimal taxTotal) { this.taxTotal = taxTotal; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public void setStatus(String status) { this.status = status; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setPeriodId(Long periodId) { this.periodId = periodId; }
    public void setJournalHeaderId(Long journalHeaderId) { this.journalHeaderId = journalHeaderId; }
}
