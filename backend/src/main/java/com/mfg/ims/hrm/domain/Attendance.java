package com.mfg.ims.hrm.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "attendance", schema = "ims")
public class Attendance extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "shift", length = 10)
    private String shift;

    @Column(name = "check_in")
    private Instant checkIn;

    @Column(name = "check_out")
    private Instant checkOut;

    @Column(name = "status", nullable = false, length = 10)
    private String status = "PRESENT";

    @Column(name = "work_hours", insertable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal workHours;

    @Column(name = "source", nullable = false, length = 10)
    private String source = "MANUAL";

    @Column(name = "remarks", length = 200)
    private String remarks;

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { this.employeeId = v; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public void setAttendanceDate(LocalDate v) { this.attendanceDate = v; }
    public String getShift() { return shift; }
    public void setShift(String v) { this.shift = v; }
    public Instant getCheckIn() { return checkIn; }
    public void setCheckIn(Instant v) { this.checkIn = v; }
    public Instant getCheckOut() { return checkOut; }
    public void setCheckOut(Instant v) { this.checkOut = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public BigDecimal getWorkHours() { return workHours; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_AUTO = "AUTO";
}
