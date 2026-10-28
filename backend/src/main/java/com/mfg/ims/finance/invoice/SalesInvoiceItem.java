package com.mfg.ims.finance.invoice;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sales_invoice_items", schema = "ims")
public class SalesInvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private SalesInvoice invoice;

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

    @Column(name = "taxable_value", precision = 18, scale = 4)
    private BigDecimal taxableValue;

    @Column(name = "tax_amount", precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "line_total", precision = 18, scale = 4)
    private BigDecimal lineTotal;

    @Column(name = "hsn_sac", length = 10)
    private String hsnSac;

    @Column(name = "dispatch_item_id")
    private Long dispatchItemId;

    public Long getId() { return id; }
    public SalesInvoice getInvoice() { return invoice; }
    public void setInvoice(SalesInvoice v) { this.invoice = v; }
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
    public BigDecimal getTaxableValue() { return taxableValue; }
    public void setTaxableValue(BigDecimal v) { this.taxableValue = v; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal v) { this.taxAmount = v; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal v) { this.lineTotal = v; }
    public String getHsnSac() { return hsnSac; }
    public void setHsnSac(String v) { this.hsnSac = v; }
    public Long getDispatchItemId() { return dispatchItemId; }
    public void setDispatchItemId(Long v) { this.dispatchItemId = v; }
}
