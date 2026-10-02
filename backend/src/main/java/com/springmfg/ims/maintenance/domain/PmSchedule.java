package com.springmfg.ims.maintenance.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "pm_schedules", schema = "ims")
public class PmSchedule extends BaseEntity {

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "task_name", nullable = false, length = 100)
    private String taskName;

    @Column(name = "frequency_days", nullable = false)
    private int frequencyDays;

    @Column(name = "last_performed_date")
    private LocalDate lastPerformedDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(nullable = false)
    private boolean active = true;

    public Long getMachineId() { return machineId; }
    public void setMachineId(Long machineId) { this.machineId = machineId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public int getFrequencyDays() { return frequencyDays; }
    public void setFrequencyDays(int frequencyDays) { this.frequencyDays = frequencyDays; }
    public LocalDate getLastPerformedDate() { return lastPerformedDate; }
    public void setLastPerformedDate(LocalDate lastPerformedDate) { this.lastPerformedDate = lastPerformedDate; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }
    public Long getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Long assignedTo) { this.assignedTo = assignedTo; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
