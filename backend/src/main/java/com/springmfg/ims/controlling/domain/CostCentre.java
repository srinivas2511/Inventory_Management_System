package com.springmfg.ims.controlling.domain;

import java.math.BigDecimal;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "cost_centres", schema = "ims")
public class CostCentre extends BaseEntity {

    @Column(name = "cost_centre_code", nullable = false, unique = true, length = 20)
    private String costCentreCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "responsible_user_id")
    private Long responsibleUserId;

    @Column(name = "plant_id")
    private Long plantId;

    @Column(name = "labour_rate_per_hour", nullable = false, precision = 18, scale = 4)
    private BigDecimal labourRatePerHour = BigDecimal.ZERO;

    @Column(name = "machine_rate_per_hour", nullable = false, precision = 18, scale = 4)
    private BigDecimal machineRatePerHour = BigDecimal.ZERO;

    @Column(name = "overhead_pct", nullable = false, precision = 7, scale = 2)
    private BigDecimal overheadPct = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    public String getCostCentreCode() { return costCentreCode; }
    public String getName() { return name; }
    public Long getParentId() { return parentId; }
    public Long getResponsibleUserId() { return responsibleUserId; }
    public Long getPlantId() { return plantId; }
    public BigDecimal getLabourRatePerHour() { return labourRatePerHour; }
    public BigDecimal getMachineRatePerHour() { return machineRatePerHour; }
    public BigDecimal getOverheadPct() { return overheadPct; }
    public boolean isActive() { return active; }

    public void setCostCentreCode(String v) { this.costCentreCode = v; }
    public void setName(String v) { this.name = v; }
    public void setParentId(Long v) { this.parentId = v; }
    public void setResponsibleUserId(Long v) { this.responsibleUserId = v; }
    public void setPlantId(Long v) { this.plantId = v; }
    public void setLabourRatePerHour(BigDecimal v) { this.labourRatePerHour = v; }
    public void setMachineRatePerHour(BigDecimal v) { this.machineRatePerHour = v; }
    public void setOverheadPct(BigDecimal v) { this.overheadPct = v; }
    public void setActive(boolean v) { this.active = v; }
}
