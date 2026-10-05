package com.springmfg.ims.maintenance.domain;

import java.time.Instant;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One piece of work performed on a maintenance request; its duration is the labour that is costed. */
@Entity
@Table(name = "maintenance_activities", schema = "ims")
public class MaintenanceActivity extends BaseEntity {

    @Column(name = "maintenance_request_id", nullable = false)
    private Long maintenanceRequestId;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "performed_by", nullable = false)
    private Long performedBy;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "spare_parts_used", length = 300)
    private String sparePartsUsed;

    public Long getMaintenanceRequestId() { return maintenanceRequestId; }
    public void setMaintenanceRequestId(Long v) { this.maintenanceRequestId = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Long getPerformedBy() { return performedBy; }
    public void setPerformedBy(Long v) { this.performedBy = v; }
    public Instant getPerformedAt() { return performedAt; }
    public void setPerformedAt(Instant v) { this.performedAt = v; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer v) { this.durationMinutes = v; }
    public String getSparePartsUsed() { return sparePartsUsed; }
    public void setSparePartsUsed(String v) { this.sparePartsUsed = v; }
}
