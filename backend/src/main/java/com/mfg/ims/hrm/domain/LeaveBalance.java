package com.mfg.ims.hrm.domain;

import java.math.BigDecimal;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "leave_balances", schema = "ims")
public class LeaveBalance extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(name = "balance_year", nullable = false)
    private int balanceYear;

    @Column(name = "entitled", nullable = false, precision = 5, scale = 1)
    private BigDecimal entitled = BigDecimal.ZERO;

    @Column(name = "carried_forward", nullable = false, precision = 5, scale = 1)
    private BigDecimal carriedForward = BigDecimal.ZERO;

    @Column(name = "used", nullable = false, precision = 5, scale = 1)
    private BigDecimal used = BigDecimal.ZERO;

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { this.employeeId = v; }
    public Long getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(Long v) { this.leaveTypeId = v; }
    public int getBalanceYear() { return balanceYear; }
    public void setBalanceYear(int v) { this.balanceYear = v; }
    public BigDecimal getEntitled() { return entitled; }
    public void setEntitled(BigDecimal v) { this.entitled = v; }
    public BigDecimal getCarriedForward() { return carriedForward; }
    public void setCarriedForward(BigDecimal v) { this.carriedForward = v; }
    public BigDecimal getUsed() { return used; }
    public void setUsed(BigDecimal v) { this.used = v; }

    public BigDecimal available() {
        return entitled.add(carriedForward).subtract(used);
    }
}
