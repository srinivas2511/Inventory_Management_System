package com.mfg.ims.procurement.vendortype;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "vendor_types", schema = "ims")
public class VendorType extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    public VendorType() {}

    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
