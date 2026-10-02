package com.springmfg.ims.sales.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "dispatch_items", schema = "ims")
public class DispatchItem extends BaseEntity {

    @Column(name = "dispatch_id", nullable = false)
    private Long dispatchId;

    @Column(name = "sales_order_item_id", nullable = false)
    private Long salesOrderItemId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "fg_batch_id", nullable = false)
    private Long fgBatchId;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "unit_price", precision = 18, scale = 4)
    private BigDecimal unitPrice;

    public DispatchItem() {}

    public Long getDispatchId() { return dispatchId; }
    public Long getSalesOrderItemId() { return salesOrderItemId; }
    public Long getProductId() { return productId; }
    public Long getFgBatchId() { return fgBatchId; }
    public BigDecimal getQuantity() { return quantity; }
    public Long getWarehouseId() { return warehouseId; }
    public Long getLocationId() { return locationId; }
    public BigDecimal getUnitPrice() { return unitPrice; }

    public void setDispatchId(Long dispatchId) { this.dispatchId = dispatchId; }
    public void setSalesOrderItemId(Long salesOrderItemId) { this.salesOrderItemId = salesOrderItemId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setFgBatchId(Long fgBatchId) { this.fgBatchId = fgBatchId; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
