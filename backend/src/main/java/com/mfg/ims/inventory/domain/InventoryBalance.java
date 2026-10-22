package com.mfg.ims.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Denormalized balance row for O(1) available-stock reads.
 * Written only by {@link com.mfg.ims.inventory.service.InventoryPostingService}.
 * Does NOT extend BaseEntity — audit columns are unnecessary on a continuously-updated projection.
 */
@Entity
@Table(name = "inventory_balances", schema = "ims")
public class InventoryBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "item_type", nullable = false, length = 10)
    private String itemType;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "material_batch_id")
    private Long materialBatchId;

    @Column(name = "fg_batch_id")
    private Long fgBatchId;

    @Column(name = "stock_status", nullable = false, length = 20)
    private String stockStatus;

    @Column(name = "qty_on_hand", nullable = false, precision = 18, scale = 3)
    private BigDecimal qtyOnHand = BigDecimal.ZERO;

    @Column(name = "qty_reserved", nullable = false, precision = 18, scale = 3)
    private BigDecimal qtyReserved = BigDecimal.ZERO;

    @Column(name = "avg_unit_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal avgUnitCost = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    public InventoryBalance() {}

    /** Available quantity: on-hand minus reserved. */
    public BigDecimal available() {
        return qtyOnHand.subtract(qtyReserved);
    }

    public Long getId() { return id; }
    public Long getWarehouseId() { return warehouseId; }
    public Long getLocationId() { return locationId; }
    public String getItemType() { return itemType; }
    public Long getMaterialId() { return materialId; }
    public Long getProductId() { return productId; }
    public Long getMaterialBatchId() { return materialBatchId; }
    public Long getFgBatchId() { return fgBatchId; }
    public String getStockStatus() { return stockStatus; }
    public BigDecimal getQtyOnHand() { return qtyOnHand; }
    public BigDecimal getQtyReserved() { return qtyReserved; }
    public BigDecimal getAvgUnitCost() { return avgUnitCost; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
    public Long getCompanyId() { return companyId; }

    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setMaterialBatchId(Long materialBatchId) { this.materialBatchId = materialBatchId; }
    public void setFgBatchId(Long fgBatchId) { this.fgBatchId = fgBatchId; }
    public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
    public void setQtyOnHand(BigDecimal qtyOnHand) { this.qtyOnHand = qtyOnHand; this.updatedAt = Instant.now(); }
    public void setQtyReserved(BigDecimal qtyReserved) { this.qtyReserved = qtyReserved; }
    public void setAvgUnitCost(BigDecimal avgUnitCost) { this.avgUnitCost = avgUnitCost; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
