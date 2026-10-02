package com.springmfg.ims.sales.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "sales_reservations", schema = "ims")
public class SalesReservation extends BaseEntity {

    @Column(name = "sales_order_item_id", nullable = false)
    private Long salesOrderItemId;

    @Column(name = "fg_batch_id", nullable = false)
    private Long fgBatchId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "reserved_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal reservedQty;

    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";

    @Column(name = "reserved_by")
    private Long reservedBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public SalesReservation() {}

    public Long getSalesOrderItemId() { return salesOrderItemId; }
    public Long getFgBatchId() { return fgBatchId; }
    public Long getWarehouseId() { return warehouseId; }
    public Long getLocationId() { return locationId; }
    public BigDecimal getReservedQty() { return reservedQty; }
    public String getStatus() { return status; }
    public Long getReservedBy() { return reservedBy; }
    public Instant getExpiresAt() { return expiresAt; }

    public void setSalesOrderItemId(Long salesOrderItemId) { this.salesOrderItemId = salesOrderItemId; }
    public void setFgBatchId(Long fgBatchId) { this.fgBatchId = fgBatchId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public void setReservedQty(BigDecimal reservedQty) { this.reservedQty = reservedQty; }
    public void setStatus(String status) { this.status = status; }
    public void setReservedBy(Long reservedBy) { this.reservedBy = reservedBy; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
