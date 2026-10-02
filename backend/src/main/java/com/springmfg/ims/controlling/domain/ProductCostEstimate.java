package com.springmfg.ims.controlling.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_cost_estimates", schema = "ims")
public class ProductCostEstimate extends BaseEntity {

    @Column(name = "estimate_number", nullable = false, unique = true, length = 20)
    private String estimateNumber;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "bom_id", nullable = false)
    private Long bomId;

    @Column(name = "cost_centre_id")
    private Long costCentreId;

    @Column(name = "estimated_at", nullable = false)
    private Instant estimatedAt = Instant.now();

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "material_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal materialCost = BigDecimal.ZERO;

    @Column(name = "labour_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal labourCost = BigDecimal.ZERO;

    @Column(name = "machine_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal machineCost = BigDecimal.ZERO;

    @Column(name = "overhead_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal overheadCost = BigDecimal.ZERO;

    @Column(name = "total_standard_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalStandardCost = BigDecimal.ZERO;

    @Column(name = "cost_lot_size", nullable = false, precision = 18, scale = 3)
    private BigDecimal costLotSize = BigDecimal.ONE;

    @Column(nullable = false, length = 10)
    private String status = "DRAFT";

    @Column(name = "released_by")
    private Long releasedBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    @OneToMany(mappedBy = "estimate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CostEstimateLine> lines = new ArrayList<>();

    public String getEstimateNumber() { return estimateNumber; }
    public Long getProductId() { return productId; }
    public Long getBomId() { return bomId; }
    public Long getCostCentreId() { return costCentreId; }
    public Instant getEstimatedAt() { return estimatedAt; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public BigDecimal getMaterialCost() { return materialCost; }
    public BigDecimal getLabourCost() { return labourCost; }
    public BigDecimal getMachineCost() { return machineCost; }
    public BigDecimal getOverheadCost() { return overheadCost; }
    public BigDecimal getTotalStandardCost() { return totalStandardCost; }
    public BigDecimal getCostLotSize() { return costLotSize; }
    public String getStatus() { return status; }
    public Long getReleasedBy() { return releasedBy; }
    public Instant getReleasedAt() { return releasedAt; }
    public List<CostEstimateLine> getLines() { return lines; }

    public void setEstimateNumber(String v) { this.estimateNumber = v; }
    public void setProductId(Long v) { this.productId = v; }
    public void setBomId(Long v) { this.bomId = v; }
    public void setCostCentreId(Long v) { this.costCentreId = v; }
    public void setEstimatedAt(Instant v) { this.estimatedAt = v; }
    public void setValidFrom(LocalDate v) { this.validFrom = v; }
    public void setValidTo(LocalDate v) { this.validTo = v; }
    public void setMaterialCost(BigDecimal v) { this.materialCost = v; }
    public void setLabourCost(BigDecimal v) { this.labourCost = v; }
    public void setMachineCost(BigDecimal v) { this.machineCost = v; }
    public void setOverheadCost(BigDecimal v) { this.overheadCost = v; }
    public void setTotalStandardCost(BigDecimal v) { this.totalStandardCost = v; }
    public void setCostLotSize(BigDecimal v) { this.costLotSize = v; }
    public void setStatus(String v) { this.status = v; }
    public void setReleasedBy(Long v) { this.releasedBy = v; }
    public void setReleasedAt(Instant v) { this.releasedAt = v; }

    public void addLine(CostEstimateLine line) {
        line.setEstimate(this);
        lines.add(line);
    }
}
