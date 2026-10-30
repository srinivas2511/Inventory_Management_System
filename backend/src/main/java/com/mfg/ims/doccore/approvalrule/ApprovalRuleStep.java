package com.mfg.ims.doccore.approvalrule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "approval_rule_steps", schema = "ims")
public class ApprovalRuleStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(name = "step_group", nullable = false)
    private int stepGroup = 1;

    @Column(name = "approver_type", nullable = false, length = 10)
    private String approverType;

    @Column(name = "approver_ref", nullable = false, length = 40)
    private String approverRef;

    @Column(name = "required_permission", length = 60)
    private String requiredPermission;

    // V87
    @Column(name = "quorum", nullable = false, length = 5)
    private String quorum = "ANY";

    public ApprovalRuleStep() {}

    public Long getId() { return id; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long v) { this.ruleId = v; }
    public int getStepNo() { return stepNo; }
    public void setStepNo(int v) { this.stepNo = v; }
    public int getStepGroup() { return stepGroup; }
    public void setStepGroup(int v) { this.stepGroup = v; }
    public String getApproverType() { return approverType; }
    public void setApproverType(String v) { this.approverType = v; }
    public String getApproverRef() { return approverRef; }
    public void setApproverRef(String v) { this.approverRef = v; }
    public String getRequiredPermission() { return requiredPermission; }
    public void setRequiredPermission(String v) { this.requiredPermission = v; }
    public String getQuorum() { return quorum; }
    public void setQuorum(String v) { this.quorum = v; }
}
