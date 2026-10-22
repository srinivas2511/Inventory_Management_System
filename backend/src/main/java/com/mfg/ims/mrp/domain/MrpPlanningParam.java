package com.mfg.ims.mrp.domain;

import java.math.BigDecimal;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "mrp_planning_params", schema = "ims")
public class MrpPlanningParam extends BaseEntity {

    @Column(name = "item_type", nullable = false, length = 10)
    private String itemType;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "planning_strategy", nullable = false, length = 3)
    private String planningStrategy = "MTS";

    @Column(name = "lot_size_rule", nullable = false, length = 10)
    private String lotSizeRule = "EXACT";

    @Column(name = "fixed_lot_qty", precision = 18, scale = 3)
    private BigDecimal fixedLotQty;

    @Column(name = "min_lot_qty", precision = 18, scale = 3)
    private BigDecimal minLotQty;

    @Column(name = "max_lot_qty", precision = 18, scale = 3)
    private BigDecimal maxLotQty;

    @Column(name = "eoq_annual_demand", precision = 18, scale = 3)
    private BigDecimal eoqAnnualDemand;

    @Column(name = "eoq_order_cost", precision = 18, scale = 4)
    private BigDecimal eoqOrderCost;

    @Column(name = "eoq_holding_cost", precision = 18, scale = 4)
    private BigDecimal eoqHoldingCost;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    public String getItemType() { return itemType; }
    public Long getMaterialId() { return materialId; }
    public Long getProductId() { return productId; }
    public String getPlanningStrategy() { return planningStrategy; }
    public String getLotSizeRule() { return lotSizeRule; }
    public BigDecimal getFixedLotQty() { return fixedLotQty; }
    public BigDecimal getMinLotQty() { return minLotQty; }
    public BigDecimal getMaxLotQty() { return maxLotQty; }
    public BigDecimal getEoqAnnualDemand() { return eoqAnnualDemand; }
    public BigDecimal getEoqOrderCost() { return eoqOrderCost; }
    public BigDecimal getEoqHoldingCost() { return eoqHoldingCost; }
    public Integer getLeadTimeDays() { return leadTimeDays; }

    public void setItemType(String v) { this.itemType = v; }
    public void setMaterialId(Long v) { this.materialId = v; }
    public void setProductId(Long v) { this.productId = v; }
    public void setPlanningStrategy(String v) { this.planningStrategy = v; }
    public void setLotSizeRule(String v) { this.lotSizeRule = v; }
    public void setFixedLotQty(BigDecimal v) { this.fixedLotQty = v; }
    public void setMinLotQty(BigDecimal v) { this.minLotQty = v; }
    public void setMaxLotQty(BigDecimal v) { this.maxLotQty = v; }
    public void setEoqAnnualDemand(BigDecimal v) { this.eoqAnnualDemand = v; }
    public void setEoqOrderCost(BigDecimal v) { this.eoqOrderCost = v; }
    public void setEoqHoldingCost(BigDecimal v) { this.eoqHoldingCost = v; }
    public void setLeadTimeDays(Integer v) { this.leadTimeDays = v; }
}
