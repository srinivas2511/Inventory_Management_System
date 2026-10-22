package com.mfg.ims.mrp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "demand_forecasts", schema = "ims")
public class DemandForecast extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(length = 300)
    private String notes;

    public Long getProductId() { return productId; }
    public LocalDate getTargetDate() { return targetDate; }
    public BigDecimal getQuantity() { return quantity; }
    public String getNotes() { return notes; }

    public void setProductId(Long v) { this.productId = v; }
    public void setTargetDate(LocalDate v) { this.targetDate = v; }
    public void setQuantity(BigDecimal v) { this.quantity = v; }
    public void setNotes(String v) { this.notes = v; }
}
