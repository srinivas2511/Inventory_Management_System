package com.mfg.ims.doccore.customfield;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "custom_field_definitions", schema = "ims")
public class CustomFieldDefinition extends BaseEntity {

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "field_key", nullable = false, length = 40)
    private String fieldKey;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "field_type", nullable = false, length = 15)
    private String fieldType;

    @Column(name = "options", columnDefinition = "jsonb")
    private String options;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = false;

    @Column(name = "show_in_list", nullable = false)
    private boolean showInList = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public CustomFieldDefinition() {}

    public String getEntityType() { return entityType; }
    public void setEntityType(String v) { this.entityType = v; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String v) { this.fieldKey = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { this.label = v; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String v) { this.fieldType = v; }
    public String getOptions() { return options; }
    public void setOptions(String v) { this.options = v; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean v) { this.mandatory = v; }
    public boolean isShowInList() { return showInList; }
    public void setShowInList(boolean v) { this.showInList = v; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
