package com.mfg.ims.production.domain;

import java.math.BigDecimal;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "production_order_materials", schema = "ims")
public class ProductionOrderMaterial extends BaseEntity {

    @Column(name = "production_order_id", nullable = false)
    private Long productionOrderId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "planned_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal plannedQty;

    @Column(name = "issued_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal issuedQty = BigDecimal.ZERO;

    @Column(name = "consumed_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal consumedQty = BigDecimal.ZERO;

    @Column(name = "returned_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal returnedQty = BigDecimal.ZERO;

    @Column(name = "scrap_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal scrapQty = BigDecimal.ZERO;

    /** GENERATED ALWAYS AS — read-only from DB. */
    @Column(name = "remaining_qty", insertable = false, updatable = false, precision = 18, scale = 3)
    private BigDecimal remainingQty;

    @Column(name = "scrap_pct_applied", precision = 5, scale = 2)
    private BigDecimal scrapPctApplied;

    @Column(name = "uom", nullable = false, length = 10)
    private String uom;

    public ProductionOrderMaterial() {}

    public Long getProductionOrderId() { return productionOrderId; }
    public void setProductionOrderId(Long v) { this.productionOrderId = v; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }

    public BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(BigDecimal v) { this.plannedQty = v; }

    public BigDecimal getIssuedQty() { return issuedQty; }
    public void setIssuedQty(BigDecimal v) { this.issuedQty = v; }

    public BigDecimal getConsumedQty() { return consumedQty; }
    public void setConsumedQty(BigDecimal v) { this.consumedQty = v; }

    public BigDecimal getReturnedQty() { return returnedQty; }
    public void setReturnedQty(BigDecimal v) { this.returnedQty = v; }

    public BigDecimal getScrapQty() { return scrapQty; }
    public void setScrapQty(BigDecimal v) { this.scrapQty = v; }

    public BigDecimal getRemainingQty() { return remainingQty; }

    public BigDecimal getScrapPctApplied() { return scrapPctApplied; }
    public void setScrapPctApplied(BigDecimal v) { this.scrapPctApplied = v; }

    public String getUom() { return uom; }
    public void setUom(String v) { this.uom = v; }
}
