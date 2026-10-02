package com.springmfg.ims.quality.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApproveFgHoldRequest(
        @NotNull BigDecimal approvedQty,
        @NotNull BigDecimal rejectedQty,
        List<FinalInspectionDecisionRequest.RejectionReasonLine> rejectionReasons,
        @NotBlank String reason
) {}
