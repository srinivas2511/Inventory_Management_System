package com.mfg.ims.mrp.domain;

import java.time.Instant;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "mrp_runs", schema = "ims")
public class MrpRun extends BaseEntity {

    @Column(name = "run_number", nullable = false, unique = true, length = 20)
    private String runNumber;

    @Column(name = "initiated_at", nullable = false)
    private Instant initiatedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "planning_horizon_days", nullable = false)
    private int planningHorizonDays;

    @Column(name = "include_safety_stock", nullable = false)
    private boolean includeSafetyStock = true;

    @Column(nullable = false, length = 12)
    private String status = "INITIATED";

    @Column(name = "initiated_by", nullable = false)
    private Long initiatedBy;

    @Column(name = "total_proposals")
    private Integer totalProposals;

    @Column(name = "capacity_overloads")
    private Integer capacityOverloads;

    @Column(length = 1000)
    private String notes;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public String getRunNumber() { return runNumber; }
    public Instant getInitiatedAt() { return initiatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getPlanningHorizonDays() { return planningHorizonDays; }
    public boolean isIncludeSafetyStock() { return includeSafetyStock; }
    public String getStatus() { return status; }
    public Long getInitiatedBy() { return initiatedBy; }
    public Integer getTotalProposals() { return totalProposals; }
    public Integer getCapacityOverloads() { return capacityOverloads; }
    public String getNotes() { return notes; }
    public String getErrorMessage() { return errorMessage; }

    public void setRunNumber(String v) { this.runNumber = v; }
    public void setInitiatedAt(Instant v) { this.initiatedAt = v; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
    public void setPlanningHorizonDays(int v) { this.planningHorizonDays = v; }
    public void setIncludeSafetyStock(boolean v) { this.includeSafetyStock = v; }
    public void setStatus(String v) { this.status = v; }
    public void setInitiatedBy(Long v) { this.initiatedBy = v; }
    public void setTotalProposals(Integer v) { this.totalProposals = v; }
    public void setCapacityOverloads(Integer v) { this.capacityOverloads = v; }
    public void setNotes(String v) { this.notes = v; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
}
