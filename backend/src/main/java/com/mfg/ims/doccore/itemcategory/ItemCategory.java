package com.mfg.ims.doccore.itemcategory;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "item_categories", schema = "ims")
public class ItemCategory extends BaseEntity {

    @Column(name = "category_code", nullable = false, length = 20)
    private String categoryCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "item_type", nullable = false, length = 10)
    private String itemType = "BOTH";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public ItemCategory() {}

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String v) { this.categoryCode = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long v) { this.parentId = v; }
    public String getItemType() { return itemType; }
    public void setItemType(String v) { this.itemType = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
