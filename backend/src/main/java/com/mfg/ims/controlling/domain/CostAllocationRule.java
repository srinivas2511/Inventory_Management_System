package com.mfg.ims.controlling.domain;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "cost_allocation_rules", schema = "ims")
public class CostAllocationRule extends BaseEntity {

    @Column(name = "cost_centre_id", nullable = false)
    private Long costCentreId;

    @Column(name = "allocation_base", nullable = false, length = 20)
    private String allocationBase;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    public CostAllocationRule() {}

    public Long getCostCentreId() { return costCentreId; }
    public void setCostCentreId(Long v) { this.costCentreId = v; }

    public String getAllocationBase() { return allocationBase; }
    public void setAllocationBase(String v) { this.allocationBase = v; }

    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
}
