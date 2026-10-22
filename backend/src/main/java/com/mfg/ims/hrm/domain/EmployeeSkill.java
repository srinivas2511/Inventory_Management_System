package com.mfg.ims.hrm.domain;

import java.time.LocalDate;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "employee_skill_matrix", schema = "ims")
public class EmployeeSkill extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "skill_type", nullable = false, length = 20)
    private String skillType;

    @Column(name = "machine_id")
    private Long machineId;

    @Column(name = "operation_id")
    private Long operationId;

    @Column(name = "skill_name", length = 80)
    private String skillName;

    @Column(name = "certification_no", length = 40)
    private String certificationNo;

    @Column(name = "proficiency_level")
    private Short proficiencyLevel;

    @Column(name = "certified_on")
    private LocalDate certifiedOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { this.employeeId = v; }
    public String getSkillType() { return skillType; }
    public void setSkillType(String v) { this.skillType = v; }
    public Long getMachineId() { return machineId; }
    public void setMachineId(Long v) { this.machineId = v; }
    public Long getOperationId() { return operationId; }
    public void setOperationId(Long v) { this.operationId = v; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String v) { this.skillName = v; }
    public String getCertificationNo() { return certificationNo; }
    public void setCertificationNo(String v) { this.certificationNo = v; }
    public Short getProficiencyLevel() { return proficiencyLevel; }
    public void setProficiencyLevel(Short v) { this.proficiencyLevel = v; }
    public LocalDate getCertifiedOn() { return certifiedOn; }
    public void setCertifiedOn(LocalDate v) { this.certifiedOn = v; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate v) { this.expiresOn = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
