package com.springmfg.ims.hrm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "employees", schema = "ims")
public class Employee extends BaseEntity {

    @Column(name = "employee_code", nullable = false, unique = true, length = 20)
    private String employeeCode;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "designation", length = 80)
    private String designation;

    @Column(name = "department", length = 60)
    private String department;

    @Column(name = "plant_id")
    private Long plantId;

    @Column(name = "cost_centre_id")
    private Long costCentreId;

    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;

    @Column(name = "date_of_leaving")
    private LocalDate dateOfLeaving;

    @Column(name = "employment_type", nullable = false, length = 15)
    private String employmentType = "PERMANENT";

    @Column(name = "pay_grade", length = 10)
    private String payGrade;

    @Column(name = "basic_pay", nullable = false, precision = 18, scale = 4)
    private BigDecimal basicPay = BigDecimal.ZERO;

    @Column(name = "fixed_travel_allowance", nullable = false, precision = 18, scale = 4)
    private BigDecimal fixedTravelAllowance = BigDecimal.ZERO;

    @Column(name = "fixed_other_allowances", nullable = false, precision = 18, scale = 4)
    private BigDecimal fixedOtherAllowances = BigDecimal.ZERO;

    @Column(name = "bank_account_no", length = 30)
    private String bankAccountNo;

    @Column(name = "bank_ifsc", length = 15)
    private String bankIfsc;

    @Column(name = "pf_number", length = 20)
    private String pfNumber;

    @Column(name = "esi_number", length = 20)
    private String esiNumber;

    @Column(name = "default_shift", length = 10)
    private String defaultShift;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String v) { this.employeeCode = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { this.firstName = v; }
    public String getLastName() { return lastName; }
    public void setLastName(String v) { this.lastName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getDesignation() { return designation; }
    public void setDesignation(String v) { this.designation = v; }
    public String getDepartment() { return department; }
    public void setDepartment(String v) { this.department = v; }
    public Long getPlantId() { return plantId; }
    public void setPlantId(Long v) { this.plantId = v; }
    public Long getCostCentreId() { return costCentreId; }
    public void setCostCentreId(Long v) { this.costCentreId = v; }
    public LocalDate getDateOfJoining() { return dateOfJoining; }
    public void setDateOfJoining(LocalDate v) { this.dateOfJoining = v; }
    public LocalDate getDateOfLeaving() { return dateOfLeaving; }
    public void setDateOfLeaving(LocalDate v) { this.dateOfLeaving = v; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String v) { this.employmentType = v; }
    public String getPayGrade() { return payGrade; }
    public void setPayGrade(String v) { this.payGrade = v; }
    public BigDecimal getBasicPay() { return basicPay; }
    public void setBasicPay(BigDecimal v) { this.basicPay = v; }
    public BigDecimal getFixedTravelAllowance() { return fixedTravelAllowance; }
    public void setFixedTravelAllowance(BigDecimal v) { this.fixedTravelAllowance = v; }
    public BigDecimal getFixedOtherAllowances() { return fixedOtherAllowances; }
    public void setFixedOtherAllowances(BigDecimal v) { this.fixedOtherAllowances = v; }
    public String getBankAccountNo() { return bankAccountNo; }
    public void setBankAccountNo(String v) { this.bankAccountNo = v; }
    public String getBankIfsc() { return bankIfsc; }
    public void setBankIfsc(String v) { this.bankIfsc = v; }
    public String getPfNumber() { return pfNumber; }
    public void setPfNumber(String v) { this.pfNumber = v; }
    public String getEsiNumber() { return esiNumber; }
    public void setEsiNumber(String v) { this.esiNumber = v; }
    public String getDefaultShift() { return defaultShift; }
    public void setDefaultShift(String v) { this.defaultShift = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }

    public String fullName() {
        return firstName + " " + lastName;
    }
}
