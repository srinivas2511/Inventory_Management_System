package com.springmfg.ims.production.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateIssueRequest(
        @NotNull Long productionOrderId,
        @NotNull Long warehouseId,
        @NotEmpty List<IssueLineRequest> lines,
        String remarks) {

    public record IssueLineRequest(
            @NotNull Long materialId,
            @NotNull @Positive BigDecimal quantity,
            Long explicitBatchId) {}
}
