package com.springmfg.ims.hrm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "training_records", schema = "ims")
public class TrainingRecord extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "programme_name", nullable = false, length = 150)
    private String programmeName;

    @Column(name = "training_type", nullable = false, length = 20)
    private String trainingType;

    @Column(name = "provider", length = 120)
    private String provider;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "hours", precision = 6, scale = 2)
    private BigDecimal hours;

    @Column(name = "status", nullable = false, length = 12)
    private String status = "PLANNED";

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "certificate_no", length = 40)
    private String certificateNo;

    @Column(name = "certified_on")
    private LocalDate certifiedOn;

    @Column(name = "renewal_due_on")
    private LocalDate renewalDueOn;

    @Column(name = "notes", length = 300)
    private String notes;

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { this.employeeId = v; }
    public String getProgrammeName() { return programmeName; }
    public void setProgrammeName(String v) { this.programmeName = v; }
    public String getTrainingType() { return trainingType; }
    public void setTrainingType(String v) { this.trainingType = v; }
    public String getProvider() { return provider; }
    public void setProvider(String v) { this.provider = v; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { this.endDate = v; }
    public BigDecimal getHours() { return hours; }
    public void setHours(BigDecimal v) { this.hours = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal v) { this.score = v; }
    public String getCertificateNo() { return certificateNo; }
    public void setCertificateNo(String v) { this.certificateNo = v; }
    public LocalDate getCertifiedOn() { return certifiedOn; }
    public void setCertifiedOn(LocalDate v) { this.certifiedOn = v; }
    public LocalDate getRenewalDueOn() { return renewalDueOn; }
    public void setRenewalDueOn(LocalDate v) { this.renewalDueOn = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
}
