package com.springmfg.ims.production.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RecordOutputRequest(
        @NotNull Long productionOperationId,
        @NotNull @PositiveOrZero BigDecimal goodQty,
        @PositiveOrZero BigDecimal rejectedQty,
        @PositiveOrZero BigDecimal scrapQty,
        Long rejectionReasonId,
        Long machineId,
        Instant startTime,
        Instant endTime,
        String shift,
        String remarks) {}
