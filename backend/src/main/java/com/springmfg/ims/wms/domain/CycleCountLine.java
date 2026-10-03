package com.springmfg.ims.wms.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.springmfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "cycle_count_lines", schema = "ims")
public class CycleCountLine extends BaseEntity {

    @Column(name = "cycle_count_id", nullable = false)
    private Long cycleCountId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "bin_id")
    private Long binId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "item_type", nullable = false, length = 10)
    private String itemType;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "stock_status", nullable = false, length = 20)
    private String stockStatus;

    @Column(name = "system_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal systemQty;

    @Column(name = "counted_qty", precision = 18, scale = 3)
    private BigDecimal countedQty;

    @Column(name = "variance_qty", precision = 18, scale = 3)
    private BigDecimal varianceQty;

    @Column(name = "adjustment_id")
    private Long adjustmentId;

    public CycleCountLine() {
    }

    public Long getCycleCountId() { return cycleCountId; }
    public void setCycleCountId(Long cycleCountId) { this.cycleCountId = cycleCountId; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int lineNo) { this.lineNo = lineNo; }
    public Long getBinId() { return binId; }
    public void setBinId(Long binId) { this.binId = binId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getStockStatus() { return stockStatus; }
    public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
    public BigDecimal getSystemQty() { return systemQty; }
    public void setSystemQty(BigDecimal systemQty) { this.systemQty = systemQty; }
    public BigDecimal getCountedQty() { return countedQty; }
    public void setCountedQty(BigDecimal countedQty) { this.countedQty = countedQty; }
    public BigDecimal getVarianceQty() { return varianceQty; }
    public void setVarianceQty(BigDecimal varianceQty) { this.varianceQty = varianceQty; }
    public Long getAdjustmentId() { return adjustmentId; }
    public void setAdjustmentId(Long adjustmentId) { this.adjustmentId = adjustmentId; }
}
