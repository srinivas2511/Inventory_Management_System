package com.springmfg.ims.hrm.domain;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "leave_types", schema = "ims")
public class LeaveType extends BaseEntity {

    @Column(name = "leave_code", nullable = false, unique = true, length = 10)
    private String leaveCode;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "max_days_per_year")
    private Short maxDaysPerYear;

    @Column(name = "carry_forward", nullable = false)
    private boolean carryForward;

    @Column(name = "max_carry_forward_days", nullable = false)
    private short maxCarryForwardDays;

    @Column(name = "paid", nullable = false)
    private boolean paid = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getLeaveCode() { return leaveCode; }
    public void setLeaveCode(String v) { this.leaveCode = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public Short getMaxDaysPerYear() { return maxDaysPerYear; }
    public void setMaxDaysPerYear(Short v) { this.maxDaysPerYear = v; }
    public boolean isCarryForward() { return carryForward; }
    public void setCarryForward(boolean v) { this.carryForward = v; }
    public short getMaxCarryForwardDays() { return maxCarryForwardDays; }
    public void setMaxCarryForwardDays(short v) { this.maxCarryForwardDays = v; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean v) { this.paid = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
