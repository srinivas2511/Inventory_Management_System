package com.mfg.ims.procurement.purchaseinvoice;

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
@Table(name = "purchase_invoice_items", schema = "ims")
public class PurchaseInvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private PurchaseInvoice invoice;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "po_item_id")
    private Long poItemId;

    @Column(name = "grn_item_id")
    private Long grnItemId;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal qty;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "discount_pct", nullable = false, precision = 9, scale = 4)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(name = "cgst_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal cgstRate = BigDecimal.ZERO;

    @Column(name = "sgst_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal sgstRate = BigDecimal.ZERO;

    @Column(name = "igst_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal igstRate = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal lineTotal;

    @Column(name = "match_status", length = 12)
    private String matchStatus;

    @Column(name = "qty_variance", precision = 18, scale = 3)
    private BigDecimal qtyVariance;

    @Column(name = "price_variance", precision = 18, scale = 4)
    private BigDecimal priceVariance;

    public Long getId() { return id; }
    public PurchaseInvoice getInvoice() { return invoice; }
    public void setInvoice(PurchaseInvoice v) { this.invoice = v; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int v) { this.lineNo = v; }
    public Long getPoItemId() { return poItemId; }
    public void setPoItemId(Long v) { this.poItemId = v; }
    public Long getGrnItemId() { return grnItemId; }
    public void setGrnItemId(Long v) { this.grnItemId = v; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }
    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal v) { this.qty = v; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal v) { this.unitPrice = v; }
    public BigDecimal getDiscountPct() { return discountPct; }
    public void setDiscountPct(BigDecimal v) { this.discountPct = v; }
    public BigDecimal getCgstRate() { return cgstRate; }
    public void setCgstRate(BigDecimal v) { this.cgstRate = v; }
    public BigDecimal getSgstRate() { return sgstRate; }
    public void setSgstRate(BigDecimal v) { this.sgstRate = v; }
    public BigDecimal getIgstRate() { return igstRate; }
    public void setIgstRate(BigDecimal v) { this.igstRate = v; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal v) { this.lineTotal = v; }
    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String v) { this.matchStatus = v; }
    public BigDecimal getQtyVariance() { return qtyVariance; }
    public void setQtyVariance(BigDecimal v) { this.qtyVariance = v; }
    public BigDecimal getPriceVariance() { return priceVariance; }
    public void setPriceVariance(BigDecimal v) { this.priceVariance = v; }
}
