package com.springmfg.ims.quality.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateFinalInspectionRequest(
        @NotNull Long fgBatchId,
        @NotNull @Positive BigDecimal inspectedQty,
        Integer sampleSize
) {}
