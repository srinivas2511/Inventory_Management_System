package com.mfg.ims.maintenance.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Spare parts issued against a maintenance request; quantity x unit cost is the spare part cost of the request. */
@Entity
@Table(name = "spare_part_issue_lines", schema = "ims")
public class SparePartIssueLine extends BaseEntity {

    @Column(name = "maintenance_request_id", nullable = false)
    private Long maintenanceRequestId;

    @Column(name = "spare_part_id", nullable = false)
    private Long sparePartId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "issued_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal issuedQty;

    @Column(name = "unit_cost", precision = 18, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "issued_by", nullable = false)
    private Long issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "remarks", length = 300)
    private String remarks;

    public Long getMaintenanceRequestId() { return maintenanceRequestId; }
    public void setMaintenanceRequestId(Long v) { this.maintenanceRequestId = v; }
    public Long getSparePartId() { return sparePartId; }
    public void setSparePartId(Long v) { this.sparePartId = v; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long v) { this.warehouseId = v; }
    public BigDecimal getIssuedQty() { return issuedQty; }
    public void setIssuedQty(BigDecimal v) { this.issuedQty = v; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal v) { this.unitCost = v; }
    public Long getIssuedBy() { return issuedBy; }
    public void setIssuedBy(Long v) { this.issuedBy = v; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant v) { this.issuedAt = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }

    public BigDecimal totalCost() {
        return unitCost == null ? BigDecimal.ZERO : issuedQty.multiply(unitCost);
    }
}
