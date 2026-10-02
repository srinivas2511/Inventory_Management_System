package com.springmfg.ims.quality.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotNull;

public record FinalInspectionDecisionRequest(
        @NotNull BigDecimal approvedQty,
        @NotNull BigDecimal rejectedQty,
        @NotNull BigDecimal holdQty,
        List<ItemResultEntry> itemResults,
        List<RejectionReasonLine> rejectionReasons,
        String remarks
) {
    public record ItemResultEntry(int seq, BigDecimal actualValue, String actualText, String deviationNote) {}
    public record RejectionReasonLine(Long rejectionReasonId, BigDecimal quantity, String remarks) {}
}
