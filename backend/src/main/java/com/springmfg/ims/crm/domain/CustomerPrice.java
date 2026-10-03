package com.springmfg.ims.crm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A price for a product; a null customer is the standard price for every customer. */
@Entity
@Table(name = "customer_prices", schema = "ims")
public class CustomerPrice extends BaseEntity {

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "min_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal minQty = BigDecimal.ONE;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(nullable = false)
    private boolean active = true;

    public Long getCustomerId() { return customerId; }
    public Long getProductId() { return productId; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getMinQty() { return minQty; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public boolean isActive() { return active; }

    public void setCustomerId(Long v) { this.customerId = v; }
    public void setProductId(Long v) { this.productId = v; }
    public void setUnitPrice(BigDecimal v) { this.unitPrice = v; }
    public void setMinQty(BigDecimal v) { this.minQty = v; }
    public void setValidFrom(LocalDate v) { this.validFrom = v; }
    public void setValidTo(LocalDate v) { this.validTo = v; }
    public void setActive(boolean v) { this.active = v; }
}
