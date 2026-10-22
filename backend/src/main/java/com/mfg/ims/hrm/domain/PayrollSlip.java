package com.mfg.ims.hrm.domain;

import java.math.BigDecimal;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "payroll_slips", schema = "ims")
public class PayrollSlip extends BaseEntity {

    @Column(name = "payroll_run_id", nullable = false)
    private Long payrollRunId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "cost_centre_id")
    private Long costCentreId;

    @Column(name = "basic_pay", nullable = false, precision = 18, scale = 4)
    private BigDecimal basicPay;

    @Column(name = "hra", nullable = false, precision = 18, scale = 4)
    private BigDecimal hra;

    @Column(name = "da", nullable = false, precision = 18, scale = 4)
    private BigDecimal da;

    @Column(name = "travel_allowance", nullable = false, precision = 18, scale = 4)
    private BigDecimal travelAllowance;

    @Column(name = "other_allowances", nullable = false, precision = 18, scale = 4)
    private BigDecimal otherAllowances;

    @Column(name = "gross_pay", nullable = false, precision = 18, scale = 4)
    private BigDecimal grossPay;

    @Column(name = "pf_employee", nullable = false, precision = 18, scale = 4)
    private BigDecimal pfEmployee;

    @Column(name = "pf_employer", nullable = false, precision = 18, scale = 4)
    private BigDecimal pfEmployer;

    @Column(name = "esi_employee", nullable = false, precision = 18, scale = 4)
    private BigDecimal esiEmployee;

    @Column(name = "esi_employer", nullable = false, precision = 18, scale = 4)
    private BigDecimal esiEmployer;

    @Column(name = "professional_tax", nullable = false, precision = 18, scale = 4)
    private BigDecimal professionalTax;

    @Column(name = "tds", nullable = false, precision = 18, scale = 4)
    private BigDecimal tds;

    @Column(name = "total_deductions", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalDeductions;

    @Column(name = "net_pay", nullable = false, precision = 18, scale = 4)
    private BigDecimal netPay;

    @Column(name = "working_days", precision = 4, scale = 1)
    private BigDecimal workingDays;

    @Column(name = "paid_days", precision = 4, scale = 1)
    private BigDecimal paidDays;

    @Column(name = "lop_days", precision = 4, scale = 1)
    private BigDecimal lopDays;

    public Long getPayrollRunId() { return payrollRunId; }
    public void setPayrollRunId(Long v) { this.payrollRunId = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { this.employeeId = v; }
    public Long getCostCentreId() { return costCentreId; }
    public void setCostCentreId(Long v) { this.costCentreId = v; }
    public BigDecimal getBasicPay() { return basicPay; }
    public void setBasicPay(BigDecimal v) { this.basicPay = v; }
    public BigDecimal getHra() { return hra; }
    public void setHra(BigDecimal v) { this.hra = v; }
    public BigDecimal getDa() { return da; }
    public void setDa(BigDecimal v) { this.da = v; }
    public BigDecimal getTravelAllowance() { return travelAllowance; }
    public void setTravelAllowance(BigDecimal v) { this.travelAllowance = v; }
    public BigDecimal getOtherAllowances() { return otherAllowances; }
    public void setOtherAllowances(BigDecimal v) { this.otherAllowances = v; }
    public BigDecimal getGrossPay() { return grossPay; }
    public void setGrossPay(BigDecimal v) { this.grossPay = v; }
    public BigDecimal getPfEmployee() { return pfEmployee; }
    public void setPfEmployee(BigDecimal v) { this.pfEmployee = v; }
    public BigDecimal getPfEmployer() { return pfEmployer; }
    public void setPfEmployer(BigDecimal v) { this.pfEmployer = v; }
    public BigDecimal getEsiEmployee() { return esiEmployee; }
    public void setEsiEmployee(BigDecimal v) { this.esiEmployee = v; }
    public BigDecimal getEsiEmployer() { return esiEmployer; }
    public void setEsiEmployer(BigDecimal v) { this.esiEmployer = v; }
    public BigDecimal getProfessionalTax() { return professionalTax; }
    public void setProfessionalTax(BigDecimal v) { this.professionalTax = v; }
    public BigDecimal getTds() { return tds; }
    public void setTds(BigDecimal v) { this.tds = v; }
    public BigDecimal getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(BigDecimal v) { this.totalDeductions = v; }
    public BigDecimal getNetPay() { return netPay; }
    public void setNetPay(BigDecimal v) { this.netPay = v; }
    public BigDecimal getWorkingDays() { return workingDays; }
    public void setWorkingDays(BigDecimal v) { this.workingDays = v; }
    public BigDecimal getPaidDays() { return paidDays; }
    public void setPaidDays(BigDecimal v) { this.paidDays = v; }
    public BigDecimal getLopDays() { return lopDays; }
    public void setLopDays(BigDecimal v) { this.lopDays = v; }
}
