package com.mfg.ims.masterdata.operation;

import java.math.BigDecimal;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "operations", schema = "ims")
public class Operation extends BaseEntity {

    @Column(name = "operation_code", nullable = false, unique = true, length = 20)
    private String operationCode;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "default_machine_type", length = 40)
    private String defaultMachineType;

    @Column(name = "standard_time_min", nullable = false, precision = 10, scale = 2)
    private BigDecimal standardTimeMin = BigDecimal.ZERO;

    @Column(name = "setup_time_min", nullable = false, precision = 10, scale = 2)
    private BigDecimal setupTimeMin = BigDecimal.ZERO;

    @Column(name = "is_inspection", nullable = false)
    private boolean isInspection = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Operation() {}

    public String getOperationCode() { return operationCode; }
    public void setOperationCode(String v) { this.operationCode = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public String getDefaultMachineType() { return defaultMachineType; }
    public void setDefaultMachineType(String v) { this.defaultMachineType = v; }

    public BigDecimal getStandardTimeMin() { return standardTimeMin; }
    public void setStandardTimeMin(BigDecimal v) { this.standardTimeMin = v; }

    public BigDecimal getSetupTimeMin() { return setupTimeMin; }
    public void setSetupTimeMin(BigDecimal v) { this.setupTimeMin = v; }

    public boolean isInspection() { return isInspection; }
    public void setInspection(boolean v) { this.isInspection = v; }

    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
