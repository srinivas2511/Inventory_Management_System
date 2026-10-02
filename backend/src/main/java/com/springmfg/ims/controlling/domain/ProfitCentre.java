package com.springmfg.ims.controlling.domain;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "profit_centres", schema = "ims")
public class ProfitCentre extends BaseEntity {

    @Column(name = "profit_centre_code", nullable = false, unique = true, length = 20)
    private String profitCentreCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    public String getProfitCentreCode() { return profitCentreCode; }
    public String getName() { return name; }
    public boolean isActive() { return active; }

    public void setProfitCentreCode(String v) { this.profitCentreCode = v; }
    public void setName(String v) { this.name = v; }
    public void setActive(boolean v) { this.active = v; }
}
