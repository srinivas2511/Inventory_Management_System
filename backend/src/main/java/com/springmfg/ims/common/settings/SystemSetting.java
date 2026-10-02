package com.springmfg.ims.common.settings;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_settings", schema = "ims")
public class SystemSetting {

    @Id
    @Column(length = 100)
    private String key;

    @Column(nullable = false, length = 500)
    private String value;

    @Column(name = "value_type", nullable = false, length = 15)
    private String valueType;

    @Column(length = 300)
    private String description;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    protected SystemSetting() {}

    public String getKey() { return key; }
    public String getValue() { return value; }
    public String getValueType() { return valueType; }
    public String getDescription() { return description; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }

    public void setValue(String value) { this.value = value; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
