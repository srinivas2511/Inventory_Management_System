package com.mfg.ims.procurement.supplierquote;

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
@Table(name = "supplier_quotes", schema = "ims")
public class SupplierQuote extends BaseEntity {

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "rfq_id", nullable = false)
    private Long rfqId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "freight", nullable = false, precision = 18, scale = 4)
    private BigDecimal freight = BigDecimal.ZERO;

    @Column(name = "payment_terms_id")
    private Long paymentTermsId;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "awarded", nullable = false)
    private boolean awarded = false;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<SupplierQuoteItem> items = new ArrayList<>();

    public String getDocNo() { return docNo; }
    public void setDocNo(String v) { this.docNo = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getRfqId() { return rfqId; }
    public void setRfqId(Long v) { this.rfqId = v; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long v) { this.supplierId = v; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate v) { this.validUntil = v; }
    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer v) { this.leadTimeDays = v; }
    public BigDecimal getFreight() { return freight; }
    public void setFreight(BigDecimal v) { this.freight = v; }
    public Long getPaymentTermsId() { return paymentTermsId; }
    public void setPaymentTermsId(Long v) { this.paymentTermsId = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public boolean isAwarded() { return awarded; }
    public void setAwarded(boolean v) { this.awarded = v; }
    public List<SupplierQuoteItem> getItems() { return items; }
}
