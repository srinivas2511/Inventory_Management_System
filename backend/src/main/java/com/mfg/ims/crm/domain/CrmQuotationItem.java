package com.mfg.ims.crm.domain;

import java.math.BigDecimal;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "crm_quotation_items", schema = "ims")
public class CrmQuotationItem extends BaseEntity {

    @Column(name = "quotation_id", nullable = false)
    private Long quotationId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "discount_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal lineTotal;

    public Long getQuotationId() { return quotationId; }
    public int getLineNo() { return lineNo; }
    public Long getProductId() { return productId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getDiscountPct() { return discountPct; }
    public BigDecimal getLineTotal() { return lineTotal; }

    public void setQuotationId(Long v) { this.quotationId = v; }
    public void setLineNo(int v) { this.lineNo = v; }
    public void setProductId(Long v) { this.productId = v; }
    public void setQuantity(BigDecimal v) { this.quantity = v; }
    public void setUnitPrice(BigDecimal v) { this.unitPrice = v; }
    public void setDiscountPct(BigDecimal v) { this.discountPct = v; }
    public void setLineTotal(BigDecimal v) { this.lineTotal = v; }
}
