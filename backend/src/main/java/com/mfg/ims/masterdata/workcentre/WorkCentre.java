package com.mfg.ims.masterdata.workcentre;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "work_centres", schema = "ims")
public class WorkCentre extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    public WorkCentre() {}

    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
