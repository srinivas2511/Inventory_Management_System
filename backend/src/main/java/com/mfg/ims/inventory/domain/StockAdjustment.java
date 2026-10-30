package com.mfg.ims.inventory.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.mfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "stock_adjustments", schema = "ims")
public class StockAdjustment extends BaseEntity {

    @Column(name = "adjustment_number", nullable = false, unique = true, length = 20)
    private String adjustmentNumber;

    @Column(name = "item_type", nullable = false, length = 10)
    private String itemType;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "stock_status", nullable = false, length = 20)
    private String stockStatus = "AVAILABLE";

    @Column(name = "system_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal systemQty;

    @Column(name = "counted_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal countedQty;

    @Column(name = "variance_value", precision = 18, scale = 4)
    private BigDecimal varianceValue;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, length = 25)
    private String status = "REQUESTED";

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "posted_transaction_id")
    private Long postedTransactionId;

    @Column(name = "stock_count_id")
    private Long stockCountId;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = false;

    public StockAdjustment() {}

    public String getAdjustmentNumber() { return adjustmentNumber; }
    public String getItemType() { return itemType; }
    public Long getMaterialId() { return materialId; }
    public Long getProductId() { return productId; }
    public Long getBatchId() { return batchId; }
    public Long getWarehouseId() { return warehouseId; }
    public Long getLocationId() { return locationId; }
    public String getStockStatus() { return stockStatus; }
    public BigDecimal getSystemQty() { return systemQty; }
    public BigDecimal getCountedQty() { return countedQty; }
    public BigDecimal getVarianceValue() { return varianceValue; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public Long getRequestedBy() { return requestedBy; }
    public Long getPostedTransactionId() { return postedTransactionId; }
    public Long getStockCountId() { return stockCountId; }

    public void setAdjustmentNumber(String adjustmentNumber) { this.adjustmentNumber = adjustmentNumber; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
    public void setSystemQty(BigDecimal systemQty) { this.systemQty = systemQty; }
    public void setCountedQty(BigDecimal countedQty) { this.countedQty = countedQty; }
    public void setVarianceValue(BigDecimal varianceValue) { this.varianceValue = varianceValue; }
    public void setReason(String reason) { this.reason = reason; }
    public void setStatus(String status) { this.status = status; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }
    public void setPostedTransactionId(Long postedTransactionId) { this.postedTransactionId = postedTransactionId; }
    public void setStockCountId(Long stockCountId) { this.stockCountId = stockCountId; }

    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean v) { this.requiresApproval = v; }

    public BigDecimal varianceQty() {
        return countedQty.subtract(systemQty);
    }
}
