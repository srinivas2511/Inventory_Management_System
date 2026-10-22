package com.mfg.ims.crm.domain;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_tags", schema = "ims")
public class CustomerTag extends BaseEntity {

    @Column(name = "tag_group", nullable = false, length = 12)
    private String tagGroup;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    public String getTagGroup() { return tagGroup; }
    public String getName() { return name; }
    public boolean isActive() { return active; }

    public void setTagGroup(String v) { this.tagGroup = v; }
    public void setName(String v) { this.name = v; }
    public void setActive(boolean v) { this.active = v; }
}
