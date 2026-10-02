package com.springmfg.ims.production.domain;

import java.math.BigDecimal;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "material_issue_lines", schema = "ims")
public class MaterialIssueLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_issue_id", nullable = false)
    private MaterialIssue materialIssue;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "material_batch_id", nullable = false)
    private Long materialBatchId;

    @Column(name = "from_location_id", nullable = false)
    private Long fromLocationId;

    @Column(name = "to_location_id", nullable = false)
    private Long toLocationId;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    public MaterialIssueLine() {}

    public MaterialIssue getMaterialIssue() { return materialIssue; }
    public void setMaterialIssue(MaterialIssue v) { this.materialIssue = v; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }

    public Long getMaterialBatchId() { return materialBatchId; }
    public void setMaterialBatchId(Long v) { this.materialBatchId = v; }

    public Long getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(Long v) { this.fromLocationId = v; }

    public Long getToLocationId() { return toLocationId; }
    public void setToLocationId(Long v) { this.toLocationId = v; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal v) { this.quantity = v; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal v) { this.unitCost = v; }
}
