package com.mfg.ims.masterdata.machine;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "machines", schema = "ims")
public class Machine extends BaseEntity {

    @Column(name = "machine_code", nullable = false, unique = true, length = 20)
    private String machineCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "machine_type", length = 40)
    private String machineType;

    @Column(name = "production_line", length = 40)
    private String productionLine;

    @Column(name = "status", nullable = false, length = 15)
    private String status = "AVAILABLE";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "cost_centre_id")
    private Long costCentreId;

    public Machine() {}

    public String getMachineCode() { return machineCode; }
    public void setMachineCode(String v) { this.machineCode = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getMachineType() { return machineType; }
    public void setMachineType(String v) { this.machineType = v; }

    public String getProductionLine() { return productionLine; }
    public void setProductionLine(String v) { this.productionLine = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public Long getCostCentreId() { return costCentreId; }
    public void setCostCentreId(Long v) { this.costCentreId = v; }

    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
