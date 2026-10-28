package com.mfg.ims.sales.orderconfirmation;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "order_confirmation_items", schema = "ims")
public class OrderConfirmationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oc_id", nullable = false)
    private OrderConfirmation oc;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_kind", nullable = false, length = 10)
    private String itemKind = "PRODUCT";

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal qty;

    @Column(name = "uom", length = 10)
    private String uom;

    @Column(name = "rate", precision = 18, scale = 4)
    private BigDecimal rate;

    @Column(name = "line_total", precision = 18, scale = 4)
    private BigDecimal lineTotal;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "customer_part_no", length = 40)
    private String customerPartNo;

    @Column(name = "production_order_id")
    private Long productionOrderId;

    @Column(name = "qty_dispatched", precision = 18, scale = 3)
    private BigDecimal qtyDispatched = BigDecimal.ZERO;

    public Long getId() { return id; }
    public OrderConfirmation getOc() { return oc; }
    public void setOc(OrderConfirmation v) { this.oc = v; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int v) { this.lineNo = v; }
    public String getItemKind() { return itemKind; }
    public void setItemKind(String v) { this.itemKind = v; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }
    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal v) { this.qty = v; }
    public String getUom() { return uom; }
    public void setUom(String v) { this.uom = v; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal v) { this.rate = v; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal v) { this.lineTotal = v; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(LocalDate v) { this.deliveryDate = v; }
    public String getCustomerPartNo() { return customerPartNo; }
    public void setCustomerPartNo(String v) { this.customerPartNo = v; }
    public Long getProductionOrderId() { return productionOrderId; }
    public void setProductionOrderId(Long v) { this.productionOrderId = v; }
    public BigDecimal getQtyDispatched() { return qtyDispatched; }
    public void setQtyDispatched(BigDecimal v) { this.qtyDispatched = v; }
}
