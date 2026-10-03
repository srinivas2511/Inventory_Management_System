package com.springmfg.ims.hrm.domain;

import java.time.LocalTime;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "shifts", schema = "ims")
public class Shift extends BaseEntity {

    @Column(name = "shift_code", nullable = false, unique = true, length = 10)
    private String shiftCode;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "grace_minutes", nullable = false)
    private short graceMinutes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getShiftCode() { return shiftCode; }
    public void setShiftCode(String v) { this.shiftCode = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime v) { this.startTime = v; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime v) { this.endTime = v; }
    public short getGraceMinutes() { return graceMinutes; }
    public void setGraceMinutes(short v) { this.graceMinutes = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
