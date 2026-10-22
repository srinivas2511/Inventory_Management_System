package com.mfg.ims.production.domain;

import java.math.BigDecimal;

import com.mfg.ims.common.domain.BaseEntity;

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

    @Column(name = "consumed_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal consumedQty = BigDecimal.ZERO;

    @Column(name = "returned_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal returnedQty = BigDecimal.ZERO;

    @Column(name = "written_off_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal writtenOffQty = BigDecimal.ZERO;

    public MaterialIssueLine() {}

    /** Issued but not yet consumed, returned or written off. */
    public BigDecimal openQty() {
        return quantity.subtract(consumedQty).subtract(returnedQty).subtract(writtenOffQty);
    }

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

    public BigDecimal getConsumedQty() { return consumedQty; }
    public void setConsumedQty(BigDecimal v) { this.consumedQty = v; }

    public BigDecimal getReturnedQty() { return returnedQty; }
    public void setReturnedQty(BigDecimal v) { this.returnedQty = v; }

    public BigDecimal getWrittenOffQty() { return writtenOffQty; }
    public void setWrittenOffQty(BigDecimal v) { this.writtenOffQty = v; }
}
