package com.mfg.ims.sales.quotation;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "quotation_items", schema = "ims")
public class QuotationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_kind", nullable = false, length = 10)
    private String itemKind = "PRODUCT";

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal qty;

    @Column(name = "uom", length = 10)
    private String uom;

    @Column(name = "rate", precision = 18, scale = 4)
    private BigDecimal rate;

    @Column(name = "discount_pct", precision = 9, scale = 4)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(name = "line_total", precision = 18, scale = 4)
    private BigDecimal lineTotal;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "customer_part_no", length = 40)
    private String customerPartNo;

    public Long getId() { return id; }
    public Quotation getQuotation() { return quotation; }
    public void setQuotation(Quotation v) { this.quotation = v; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int v) { this.lineNo = v; }
    public String getItemKind() { return itemKind; }
    public void setItemKind(String v) { this.itemKind = v; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }
    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal v) { this.qty = v; }
    public String getUom() { return uom; }
    public void setUom(String v) { this.uom = v; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal v) { this.rate = v; }
    public BigDecimal getDiscountPct() { return discountPct; }
    public void setDiscountPct(BigDecimal v) { this.discountPct = v; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal v) { this.lineTotal = v; }
    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer v) { this.leadTimeDays = v; }
    public String getCustomerPartNo() { return customerPartNo; }
    public void setCustomerPartNo(String v) { this.customerPartNo = v; }
}
