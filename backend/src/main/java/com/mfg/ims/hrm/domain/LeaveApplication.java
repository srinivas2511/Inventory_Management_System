package com.mfg.ims.hrm.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "leave_applications", schema = "ims")
public class LeaveApplication extends BaseEntity {

    @Column(name = "application_number", nullable = false, unique = true, length = 20)
    private String applicationNumber;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "half_day", nullable = false)
    private boolean halfDay;

    @Column(name = "days", nullable = false, precision = 4, scale = 1)
    private BigDecimal days;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "status", nullable = false, length = 10)
    private String status = "PENDING";

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", length = 300)
    private String decisionReason;

    public String getApplicationNumber() { return applicationNumber; }
    public void setApplicationNumber(String v) { this.applicationNumber = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { this.employeeId = v; }
    public Long getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(Long v) { this.leaveTypeId = v; }
    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate v) { this.fromDate = v; }
    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate v) { this.toDate = v; }
    public boolean isHalfDay() { return halfDay; }
    public void setHalfDay(boolean v) { this.halfDay = v; }
    public BigDecimal getDays() { return days; }
    public void setDays(BigDecimal v) { this.days = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getDecidedBy() { return decidedBy; }
    public void setDecidedBy(Long v) { this.decidedBy = v; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant v) { this.decidedAt = v; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String v) { this.decisionReason = v; }
}
