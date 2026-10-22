package com.mfg.ims.inventory.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.mfg.ims.common.domain.BaseEntity;
import com.mfg.ims.product.domain.Product;

@Entity
@Table(name = "fg_batches", schema = "ims")
public class FgBatch extends BaseEntity {

    @Column(name = "batch_number", nullable = false, unique = true, length = 30)
    private String batchNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "production_order_id", nullable = false)
    private Long productionOrderId;

    @Column(name = "produced_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal producedQty;

    @Column(name = "approved_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal approvedQty = BigDecimal.ZERO;

    @Column(name = "rejected_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal rejectedQty = BigDecimal.ZERO;

    @Column(name = "remaining_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal remainingQty = BigDecimal.ZERO;

    @Column(name = "produced_on", nullable = false)
    private LocalDate producedOn;

    @Column(name = "quality_status", nullable = false, length = 25)
    private String qualityStatus = "PRODUCTION_COMPLETED";

    @Column(name = "unit_cost", precision = 18, scale = 4)
    private BigDecimal unitCost;

    public FgBatch() {}

    public String getBatchNumber() { return batchNumber; }
    public Product getProduct() { return product; }
    public Long getProductionOrderId() { return productionOrderId; }
    public BigDecimal getProducedQty() { return producedQty; }
    public BigDecimal getApprovedQty() { return approvedQty; }
    public BigDecimal getRejectedQty() { return rejectedQty; }
    public BigDecimal getRemainingQty() { return remainingQty; }
    public LocalDate getProducedOn() { return producedOn; }
    public String getQualityStatus() { return qualityStatus; }
    public BigDecimal getUnitCost() { return unitCost; }

    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    public void setProduct(Product product) { this.product = product; }
    public void setProductionOrderId(Long productionOrderId) { this.productionOrderId = productionOrderId; }
    public void setProducedQty(BigDecimal producedQty) { this.producedQty = producedQty; }
    public void setApprovedQty(BigDecimal approvedQty) { this.approvedQty = approvedQty; }
    public void setRejectedQty(BigDecimal rejectedQty) { this.rejectedQty = rejectedQty; }
    public void setRemainingQty(BigDecimal remainingQty) { this.remainingQty = remainingQty; }
    public void setProducedOn(LocalDate producedOn) { this.producedOn = producedOn; }
    public void setQualityStatus(String qualityStatus) { this.qualityStatus = qualityStatus; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
}
