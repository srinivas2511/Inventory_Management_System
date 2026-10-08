package com.springmfg.ims.modules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "module_activation", schema = "ims")
public class ModuleActivation {

    @Id
    @Column(name = "module_code", length = 20)
    private String moduleCode;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    public String getModuleCode() { return moduleCode; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public Long getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
    public void setActive(boolean active) { this.active = active; }
    public void setChangedBy(Long changedBy) { this.changedBy = changedBy; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
}
