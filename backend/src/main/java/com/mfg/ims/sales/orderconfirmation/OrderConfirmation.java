package com.mfg.ims.sales.orderconfirmation;

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
@Table(name = "order_confirmations", schema = "ims")
public class OrderConfirmation extends BaseEntity {

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(name = "doc_date", nullable = false)
    private LocalDate docDate;

    @Column(name = "status", nullable = false, length = 25)
    private String status = "DRAFT";

    @Column(name = "oc_kind", nullable = false, length = 10)
    private String ocKind = "DOMESTIC";

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "quotation_id")
    private Long quotationId;

    @Column(name = "customer_po_no", length = 60)
    private String customerPoNo;

    @Column(name = "customer_po_date")
    private LocalDate customerPoDate;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "incoterm", length = 10)
    private String incoterm;

    @Column(name = "grand_total", precision = 18, scale = 4)
    private BigDecimal grandTotal;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    @Column(name = "sales_order_id", unique = true)
    private Long salesOrderId;

    @Column(name = "copied_from_id")
    private Long copiedFromId;

    @Column(name = "original_oc_id")
    private Long originalOcId;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @OneToMany(mappedBy = "oc", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<OrderConfirmationItem> items = new ArrayList<>();

    public String getDocNo() { return docNo; }
    public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; }
    public void setDocDate(LocalDate v) { this.docDate = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getOcKind() { return ocKind; }
    public void setOcKind(String v) { this.ocKind = v; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long v) { this.customerId = v; }
    public Long getQuotationId() { return quotationId; }
    public void setQuotationId(Long v) { this.quotationId = v; }
    public String getCustomerPoNo() { return customerPoNo; }
    public void setCustomerPoNo(String v) { this.customerPoNo = v; }
    public LocalDate getCustomerPoDate() { return customerPoDate; }
    public void setCustomerPoDate(LocalDate v) { this.customerPoDate = v; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(LocalDate v) { this.deliveryDate = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal v) { this.exchangeRate = v; }
    public String getIncoterm() { return incoterm; }
    public void setIncoterm(String v) { this.incoterm = v; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal v) { this.grandTotal = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
    public Long getSalesOrderId() { return salesOrderId; }
    public void setSalesOrderId(Long v) { this.salesOrderId = v; }
    public Long getCopiedFromId() { return copiedFromId; }
    public void setCopiedFromId(Long v) { this.copiedFromId = v; }
    public Long getOriginalOcId() { return originalOcId; }
    public void setOriginalOcId(Long v) { this.originalOcId = v; }
    public Long getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(Long v) { this.submittedBy = v; }
    public List<OrderConfirmationItem> getItems() { return items; }
}
