package com.mfg.ims.sales.quotation;

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
@Table(name = "quotations", schema = "ims")
public class Quotation extends BaseEntity {

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(name = "doc_date", nullable = false)
    private LocalDate docDate;

    @Column(name = "status", nullable = false, length = 25)
    private String status = "DRAFT";

    @Column(name = "revision_no", nullable = false)
    private int revisionNo = 0;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "salesperson_id")
    private Long salespersonId;

    @Column(name = "payment_terms_id")
    private Long paymentTermsId;

    @Column(name = "delivery_terms", length = 200)
    private String deliveryTerms;

    @Column(name = "enquiry_ref", length = 60)
    private String enquiryRef;

    @Column(name = "lost_reason", length = 200)
    private String lostReason;

    @Column(name = "converted_to_type", length = 25)
    private String convertedToType;

    @Column(name = "converted_to_id")
    private Long convertedToId;

    @Column(name = "grand_total", precision = 18, scale = 4)
    private BigDecimal grandTotal;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<QuotationItem> items = new ArrayList<>();

    public String getDocNo() { return docNo; }
    public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; }
    public void setDocDate(LocalDate v) { this.docDate = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public int getRevisionNo() { return revisionNo; }
    public void setRevisionNo(int v) { this.revisionNo = v; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long v) { this.customerId = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal v) { this.exchangeRate = v; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate v) { this.validUntil = v; }
    public Long getSalespersonId() { return salespersonId; }
    public void setSalespersonId(Long v) { this.salespersonId = v; }
    public Long getPaymentTermsId() { return paymentTermsId; }
    public void setPaymentTermsId(Long v) { this.paymentTermsId = v; }
    public String getDeliveryTerms() { return deliveryTerms; }
    public void setDeliveryTerms(String v) { this.deliveryTerms = v; }
    public String getEnquiryRef() { return enquiryRef; }
    public void setEnquiryRef(String v) { this.enquiryRef = v; }
    public String getLostReason() { return lostReason; }
    public void setLostReason(String v) { this.lostReason = v; }
    public String getConvertedToType() { return convertedToType; }
    public void setConvertedToType(String v) { this.convertedToType = v; }
    public Long getConvertedToId() { return convertedToId; }
    public void setConvertedToId(Long v) { this.convertedToId = v; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal v) { this.grandTotal = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
    public List<QuotationItem> getItems() { return items; }
}
