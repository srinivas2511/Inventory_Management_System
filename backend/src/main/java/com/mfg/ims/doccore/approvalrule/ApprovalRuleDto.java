package com.mfg.ims.doccore.approvalrule;

import java.util.List;

public record ApprovalRuleDto(
        Long id,
        String documentType,
        String ruleName,
        String conditionExpr,
        boolean active,
        int priority,
        String executionMode,
        List<StepDto> steps) {

    public record StepDto(Long id, int stepNo, int stepGroup, String approverType, String approverRef,
                          String requiredPermission, String quorum) {}

    public static ApprovalRuleDto from(ApprovalRule r, List<ApprovalRuleStep> steps) {
        return new ApprovalRuleDto(r.getId(), r.getDocumentType(), r.getRuleName(),
                r.getConditionExpr(), r.isActive(), r.getPriority(), r.getExecutionMode(),
                steps.stream().map(s -> new StepDto(s.getId(), s.getStepNo(), s.getStepGroup(),
                        s.getApproverType(), s.getApproverRef(), s.getRequiredPermission(), s.getQuorum())).toList());
    }
}
