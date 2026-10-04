package com.springmfg.ims.document.domain;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_categories", schema = "ims")
public class DocumentCategory extends BaseEntity {

    @Column(name = "category_code", nullable = false, unique = true, length = 20)
    private String categoryCode;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "retention_years")
    private Integer retentionYears;

    @Column(name = "required_authority", length = 40)
    private String requiredAuthority;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String v) { this.categoryCode = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public Integer getRetentionYears() { return retentionYears; }
    public void setRetentionYears(Integer v) { this.retentionYears = v; }
    public String getRequiredAuthority() { return requiredAuthority; }
    public void setRequiredAuthority(String v) { this.requiredAuthority = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
