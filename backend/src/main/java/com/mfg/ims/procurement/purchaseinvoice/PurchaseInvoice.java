package com.mfg.ims.procurement.purchaseinvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_invoices", schema = "ims")
public class PurchaseInvoice extends BaseEntity {

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "supplier_invoice_no", nullable = false, length = 40)
    private String supplierInvoiceNo;

    @Column(name = "supplier_invoice_date", nullable = false)
    private LocalDate supplierInvoiceDate;

    @Column(name = "po_id")
    private Long poId;

    @Column(name = "place_of_supply", length = 2)
    private String placeOfSupply;

    @Column(name = "reverse_charge", nullable = false)
    private boolean reverseCharge = false;

    @Column(name = "tds_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal tdsAmount = BigDecimal.ZERO;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "match_status", nullable = false, length = 12)
    private String matchStatus = "MATCHED";

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "ap_balance", precision = 18, scale = 4)
    private BigDecimal apBalance;

    @Column(name = "subtotal", nullable = false, precision = 18, scale = 4)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 18, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<PurchaseInvoiceItem> items = new ArrayList<>();

    public String getDocNo() { return docNo; }
    public void setDocNo(String v) { this.docNo = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long v) { this.supplierId = v; }
    public String getSupplierInvoiceNo() { return supplierInvoiceNo; }
    public void setSupplierInvoiceNo(String v) { this.supplierInvoiceNo = v; }
    public LocalDate getSupplierInvoiceDate() { return supplierInvoiceDate; }
    public void setSupplierInvoiceDate(LocalDate v) { this.supplierInvoiceDate = v; }
    public Long getPoId() { return poId; }
    public void setPoId(Long v) { this.poId = v; }
    public String getPlaceOfSupply() { return placeOfSupply; }
    public void setPlaceOfSupply(String v) { this.placeOfSupply = v; }
    public boolean isReverseCharge() { return reverseCharge; }
    public void setReverseCharge(boolean v) { this.reverseCharge = v; }
    public BigDecimal getTdsAmount() { return tdsAmount; }
    public void setTdsAmount(BigDecimal v) { this.tdsAmount = v; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate v) { this.dueDate = v; }
    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String v) { this.matchStatus = v; }
    public Long getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(Long v) { this.journalEntryId = v; }
    public BigDecimal getApBalance() { return apBalance; }
    public void setApBalance(BigDecimal v) { this.apBalance = v; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal v) { this.subtotal = v; }
    public BigDecimal getTaxTotal() { return taxTotal; }
    public void setTaxTotal(BigDecimal v) { this.taxTotal = v; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal v) { this.total = v; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal v) { this.paidAmount = v; }
    public List<PurchaseInvoiceItem> getItems() { return items; }
}
