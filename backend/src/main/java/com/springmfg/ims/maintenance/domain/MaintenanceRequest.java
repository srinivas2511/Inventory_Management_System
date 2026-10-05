package com.springmfg.ims.maintenance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "maintenance_requests", schema = "ims")
public class MaintenanceRequest extends BaseEntity {

    @Column(name = "request_number", nullable = false, unique = true, length = 20)
    private String requestNumber;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType = "CORRECTIVE";

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int priority = 3;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 15)
    private String status = "OPEN";

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "resolution_notes", length = 500)
    private String resolutionNotes;

    @Column(name = "labour_hours", precision = 10, scale = 2)
    private BigDecimal labourHours;

    @Column(name = "labour_cost", precision = 18, scale = 4)
    private BigDecimal labourCost;

    @Column(name = "cost_centre_id")
    private Long costCentreId;

    @Column(name = "labour_journal_header_id")
    private Long labourJournalHeaderId;

    @Column(name = "cost_posted_at")
    private Instant costPostedAt;

    public String getRequestNumber() { return requestNumber; }
    public void setRequestNumber(String requestNumber) { this.requestNumber = requestNumber; }
    public Long getMachineId() { return machineId; }
    public void setMachineId(Long machineId) { this.machineId = machineId; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }
    public Long getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Long assignedTo) { this.assignedTo = assignedTo; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public BigDecimal getLabourHours() { return labourHours; }
    public void setLabourHours(BigDecimal v) { this.labourHours = v; }
    public BigDecimal getLabourCost() { return labourCost; }
    public void setLabourCost(BigDecimal v) { this.labourCost = v; }
    public Long getCostCentreId() { return costCentreId; }
    public void setCostCentreId(Long v) { this.costCentreId = v; }
    public Long getLabourJournalHeaderId() { return labourJournalHeaderId; }
    public void setLabourJournalHeaderId(Long v) { this.labourJournalHeaderId = v; }
    public Instant getCostPostedAt() { return costPostedAt; }
    public void setCostPostedAt(Instant v) { this.costPostedAt = v; }
}
