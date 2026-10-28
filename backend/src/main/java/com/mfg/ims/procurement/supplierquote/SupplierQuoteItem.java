package com.mfg.ims.procurement.supplierquote;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "supplier_quote_items", schema = "ims")
public class SupplierQuoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private SupplierQuote quote;

    @Column(name = "rfq_item_id", nullable = false)
    private Long rfqItemId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "tax_pct", nullable = false, precision = 9, scale = 4)
    private BigDecimal taxPct = BigDecimal.ZERO;

    @Column(name = "moq", precision = 18, scale = 3)
    private BigDecimal moq;

    @Column(name = "awarded_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal awardedQty = BigDecimal.ZERO;

    @Column(name = "notes", length = 200)
    private String notes;

    public Long getId() { return id; }
    public SupplierQuote getQuote() { return quote; }
    public void setQuote(SupplierQuote v) { this.quote = v; }
    public Long getRfqItemId() { return rfqItemId; }
    public void setRfqItemId(Long v) { this.rfqItemId = v; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int v) { this.lineNo = v; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal v) { this.unitPrice = v; }
    public BigDecimal getTaxPct() { return taxPct; }
    public void setTaxPct(BigDecimal v) { this.taxPct = v; }
    public BigDecimal getMoq() { return moq; }
    public void setMoq(BigDecimal v) { this.moq = v; }
    public BigDecimal getAwardedQty() { return awardedQty; }
    public void setAwardedQty(BigDecimal v) { this.awardedQty = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
}
