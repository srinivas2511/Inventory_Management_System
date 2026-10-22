package com.mfg.ims.controlling.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "cost_estimate_lines", schema = "ims")
public class CostEstimateLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private ProductCostEstimate estimate;

    @Column(name = "cost_element", nullable = false, length = 15)
    private String costElement;

    @Column(name = "item_description", length = 100)
    private String itemDescription;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "operation_id")
    private Long operationId;

    @Column(precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(length = 10)
    private String uom;

    @Column(name = "unit_rate", precision = 18, scale = 4)
    private BigDecimal unitRate;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    public Long getId() { return id; }
    public ProductCostEstimate getEstimate() { return estimate; }
    public String getCostElement() { return costElement; }
    public String getItemDescription() { return itemDescription; }
    public Long getMaterialId() { return materialId; }
    public Long getOperationId() { return operationId; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUom() { return uom; }
    public BigDecimal getUnitRate() { return unitRate; }
    public BigDecimal getAmount() { return amount; }

    public void setEstimate(ProductCostEstimate v) { this.estimate = v; }
    public void setCostElement(String v) { this.costElement = v; }
    public void setItemDescription(String v) { this.itemDescription = v; }
    public void setMaterialId(Long v) { this.materialId = v; }
    public void setOperationId(Long v) { this.operationId = v; }
    public void setQuantity(BigDecimal v) { this.quantity = v; }
    public void setUom(String v) { this.uom = v; }
    public void setUnitRate(BigDecimal v) { this.unitRate = v; }
    public void setAmount(BigDecimal v) { this.amount = v; }
}
