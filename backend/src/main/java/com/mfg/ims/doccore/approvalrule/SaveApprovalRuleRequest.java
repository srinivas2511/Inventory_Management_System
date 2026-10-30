package com.mfg.ims.doccore.approvalrule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveApprovalRuleRequest(
        @NotBlank @Size(max = 40) String documentType,
        @NotBlank @Size(max = 100) String ruleName,
        String conditionExpr,
        int priority,
        String executionMode,
        @NotNull List<StepRequest> steps) {

    public record StepRequest(int stepNo, int stepGroup, @NotBlank String approverType,
                               @NotBlank String approverRef, String requiredPermission, String quorum) {}
}
