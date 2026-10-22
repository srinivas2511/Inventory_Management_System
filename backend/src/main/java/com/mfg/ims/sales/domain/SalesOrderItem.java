package com.mfg.ims.sales.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.mfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "sales_order_items", schema = "ims")
public class SalesOrderItem extends BaseEntity {

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "ordered_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal orderedQty;

    @Column(name = "reserved_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal reservedQty = BigDecimal.ZERO;

    @Column(name = "packed_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal packedQty = BigDecimal.ZERO;

    @Column(name = "dispatched_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal dispatchedQty = BigDecimal.ZERO;

    @Column(name = "unit_price", precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "customer_spec_ref", length = 100)
    private String customerSpecRef;

    @Column(length = 300)
    private String notes;

    public SalesOrderItem() {}

    public Long getSalesOrderId() { return salesOrderId; }
    public int getLineNo() { return lineNo; }
    public Long getProductId() { return productId; }
    public BigDecimal getOrderedQty() { return orderedQty; }
    public BigDecimal getReservedQty() { return reservedQty; }
    public BigDecimal getPackedQty() { return packedQty; }
    public BigDecimal getDispatchedQty() { return dispatchedQty; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getCustomerSpecRef() { return customerSpecRef; }
    public String getNotes() { return notes; }

    public void setSalesOrderId(Long salesOrderId) { this.salesOrderId = salesOrderId; }
    public void setLineNo(int lineNo) { this.lineNo = lineNo; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setOrderedQty(BigDecimal orderedQty) { this.orderedQty = orderedQty; }
    public void setReservedQty(BigDecimal reservedQty) { this.reservedQty = reservedQty; }
    public void setPackedQty(BigDecimal packedQty) { this.packedQty = packedQty; }
    public void setDispatchedQty(BigDecimal dispatchedQty) { this.dispatchedQty = dispatchedQty; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public void setCustomerSpecRef(String customerSpecRef) { this.customerSpecRef = customerSpecRef; }
    public void setNotes(String notes) { this.notes = notes; }
}
