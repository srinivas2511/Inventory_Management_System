package com.springmfg.ims.controlling.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "production_order_variances", schema = "ims")
public class ProductionOrderVariance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "production_order_id", nullable = false, unique = true)
    private Long productionOrderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "estimate_id")
    private Long estimateId;

    @Column(name = "good_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal goodQty = BigDecimal.ZERO;

    @Column(name = "standard_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal standardCost = BigDecimal.ZERO;

    @Column(name = "actual_material_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal actualMaterialCost = BigDecimal.ZERO;

    @Column(name = "actual_labour_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal actualLabourCost = BigDecimal.ZERO;

    @Column(name = "actual_machine_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal actualMachineCost = BigDecimal.ZERO;

    @Column(name = "actual_overhead_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal actualOverheadCost = BigDecimal.ZERO;

    @Column(name = "actual_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal actualCost = BigDecimal.ZERO;

    @Column(name = "price_variance", nullable = false, precision = 18, scale = 4)
    private BigDecimal priceVariance = BigDecimal.ZERO;

    @Column(name = "quantity_variance", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantityVariance = BigDecimal.ZERO;

    @Column(name = "efficiency_variance", nullable = false, precision = 18, scale = 4)
    private BigDecimal efficiencyVariance = BigDecimal.ZERO;

    @Column(name = "other_variance", nullable = false, precision = 18, scale = 4)
    private BigDecimal otherVariance = BigDecimal.ZERO;

    @Column(name = "total_variance", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalVariance = BigDecimal.ZERO;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "journal_header_id")
    private Long journalHeaderId;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt = Instant.now();

    @Column(name = "calculated_by")
    private Long calculatedBy;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId = 1L;

    public Long getId() { return id; }
    public Long getProductionOrderId() { return productionOrderId; }
    public Long getProductId() { return productId; }
    public Long getEstimateId() { return estimateId; }
    public BigDecimal getGoodQty() { return goodQty; }
    public BigDecimal getStandardCost() { return standardCost; }
    public BigDecimal getActualMaterialCost() { return actualMaterialCost; }
    public BigDecimal getActualLabourCost() { return actualLabourCost; }
    public BigDecimal getActualMachineCost() { return actualMachineCost; }
    public BigDecimal getActualOverheadCost() { return actualOverheadCost; }
    public BigDecimal getActualCost() { return actualCost; }
    public BigDecimal getPriceVariance() { return priceVariance; }
    public BigDecimal getQuantityVariance() { return quantityVariance; }
    public BigDecimal getEfficiencyVariance() { return efficiencyVariance; }
    public BigDecimal getOtherVariance() { return otherVariance; }
    public BigDecimal getTotalVariance() { return totalVariance; }
    public String getStatus() { return status; }
    public Long getJournalHeaderId() { return journalHeaderId; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public Long getCalculatedBy() { return calculatedBy; }

    public void setProductionOrderId(Long v) { this.productionOrderId = v; }
    public void setProductId(Long v) { this.productId = v; }
    public void setEstimateId(Long v) { this.estimateId = v; }
    public void setGoodQty(BigDecimal v) { this.goodQty = v; }
    public void setStandardCost(BigDecimal v) { this.standardCost = v; }
    public void setActualMaterialCost(BigDecimal v) { this.actualMaterialCost = v; }
    public void setActualLabourCost(BigDecimal v) { this.actualLabourCost = v; }
    public void setActualMachineCost(BigDecimal v) { this.actualMachineCost = v; }
    public void setActualOverheadCost(BigDecimal v) { this.actualOverheadCost = v; }
    public void setActualCost(BigDecimal v) { this.actualCost = v; }
    public void setPriceVariance(BigDecimal v) { this.priceVariance = v; }
    public void setQuantityVariance(BigDecimal v) { this.quantityVariance = v; }
    public void setEfficiencyVariance(BigDecimal v) { this.efficiencyVariance = v; }
    public void setOtherVariance(BigDecimal v) { this.otherVariance = v; }
    public void setTotalVariance(BigDecimal v) { this.totalVariance = v; }
    public void setStatus(String v) { this.status = v; }
    public void setJournalHeaderId(Long v) { this.journalHeaderId = v; }
    public void setCalculatedAt(Instant v) { this.calculatedAt = v; }
    public void setCalculatedBy(Long v) { this.calculatedBy = v; }
}
