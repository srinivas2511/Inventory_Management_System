package com.mfg.ims.doccore.approvalrule;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "approval_rules", schema = "ims")
public class ApprovalRule extends BaseEntity {

    @Column(name = "document_type", nullable = false, length = 40)
    private String documentType;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Column(name = "condition_expr", columnDefinition = "TEXT")
    private String conditionExpr;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "priority", nullable = false)
    private int priority = 0;

    // V87
    @Column(name = "execution_mode", nullable = false, length = 10)
    private String executionMode = "MANUAL";

    public ApprovalRule() {}

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String v) { this.documentType = v; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String v) { this.ruleName = v; }
    public String getConditionExpr() { return conditionExpr; }
    public void setConditionExpr(String v) { this.conditionExpr = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public int getPriority() { return priority; }
    public void setPriority(int v) { this.priority = v; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String v) { this.executionMode = v; }
}
