package com.springmfg.ims.wms.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "putaway_strategy_rules", schema = "ims")
public class PutAwayStrategyRule extends BaseEntity {

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "material_type", length = 40)
    private String materialType;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 15)
    private WmsEnums.PutAwayStrategy strategy;

    public PutAwayStrategyRule() {
    }

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getMaterialType() { return materialType; }
    public void setMaterialType(String materialType) { this.materialType = materialType; }
    public WmsEnums.PutAwayStrategy getStrategy() { return strategy; }
    public void setStrategy(WmsEnums.PutAwayStrategy strategy) { this.strategy = strategy; }
}
