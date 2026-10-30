package com.mfg.ims.finance.invoice;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_invoices", schema = "ims")
public class SalesInvoice extends BaseEntity {

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(name = "doc_date", nullable = false)
    private LocalDate docDate;

    @Column(name = "status", nullable = false, length = 25)
    private String status = "DRAFT";

    @Column(name = "invoice_kind", nullable = false, length = 20)
    private String invoiceKind = "TAX";

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "place_of_supply", length = 2)
    private String placeOfSupply;

    @Column(name = "supply_type", nullable = false, length = 15)
    private String supplyType = "B2B";

    @Column(name = "reverse_charge", nullable = false)
    private boolean reverseCharge = false;

    @Column(name = "sales_order_id")
    private Long salesOrderId;

    @Column(name = "oc_id")
    private Long ocId;

    @Column(name = "dispatch_id")
    private Long dispatchId;

    @Column(name = "customer_po_no", length = 60)
    private String customerPoNo;

    @Column(name = "payment_terms_id")
    private Long paymentTermsId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "taxable_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "tcs_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal tcsAmount = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "balance_inr", precision = 18, scale = 4)
    private BigDecimal balanceInr;

    @Column(name = "einvoice_status", nullable = false, length = 14)
    private String einvoiceStatus = "NOT_REQUIRED";

    @Column(name = "irn", length = 64)
    private String irn;

    @Column(name = "ack_no", length = 30)
    private String ackNo;

    @Column(name = "eway_bill_no", length = 20)
    private String ewayBillNo;

    @Column(name = "cancelled_reason", length = 300)
    private String cancelledReason;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    @Column(name = "service_confirmation_id")
    private Long serviceConfirmationId;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<SalesInvoiceItem> items = new ArrayList<>();

    public String getDocNo() { return docNo; }
    public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; }
    public void setDocDate(LocalDate v) { this.docDate = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getInvoiceKind() { return invoiceKind; }
    public void setInvoiceKind(String v) { this.invoiceKind = v; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long v) { this.customerId = v; }
    public String getPlaceOfSupply() { return placeOfSupply; }
    public void setPlaceOfSupply(String v) { this.placeOfSupply = v; }
    public String getSupplyType() { return supplyType; }
    public void setSupplyType(String v) { this.supplyType = v; }
    public boolean isReverseCharge() { return reverseCharge; }
    public void setReverseCharge(boolean v) { this.reverseCharge = v; }
    public Long getSalesOrderId() { return salesOrderId; }
    public void setSalesOrderId(Long v) { this.salesOrderId = v; }
    public Long getOcId() { return ocId; }
    public void setOcId(Long v) { this.ocId = v; }
    public Long getDispatchId() { return dispatchId; }
    public void setDispatchId(Long v) { this.dispatchId = v; }
    public String getCustomerPoNo() { return customerPoNo; }
    public void setCustomerPoNo(String v) { this.customerPoNo = v; }
    public Long getPaymentTermsId() { return paymentTermsId; }
    public void setPaymentTermsId(Long v) { this.paymentTermsId = v; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate v) { this.dueDate = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal v) { this.exchangeRate = v; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal v) { this.taxableAmount = v; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal v) { this.taxAmount = v; }
    public BigDecimal getTcsAmount() { return tcsAmount; }
    public void setTcsAmount(BigDecimal v) { this.tcsAmount = v; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal v) { this.grandTotal = v; }
    public BigDecimal getBalanceInr() { return balanceInr; }
    public void setBalanceInr(BigDecimal v) { this.balanceInr = v; }
    public String getEinvoiceStatus() { return einvoiceStatus; }
    public void setEinvoiceStatus(String v) { this.einvoiceStatus = v; }
    public String getIrn() { return irn; }
    public void setIrn(String v) { this.irn = v; }
    public String getAckNo() { return ackNo; }
    public void setAckNo(String v) { this.ackNo = v; }
    public String getEwayBillNo() { return ewayBillNo; }
    public void setEwayBillNo(String v) { this.ewayBillNo = v; }
    public String getCancelledReason() { return cancelledReason; }
    public void setCancelledReason(String v) { this.cancelledReason = v; }
    public Long getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(Long v) { this.journalEntryId = v; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant v) { this.postedAt = v; }
    public Long getPostedBy() { return postedBy; }
    public void setPostedBy(Long v) { this.postedBy = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
    public Long getServiceConfirmationId() { return serviceConfirmationId; }
    public void setServiceConfirmationId(Long v) { this.serviceConfirmationId = v; }
    public List<SalesInvoiceItem> getItems() { return items; }
}
