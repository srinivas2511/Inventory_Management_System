package com.mfg.ims.quality.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateInProcessInspectionRequest(
        @NotNull Long productionOperationId,
        @NotNull @Positive BigDecimal inspectedQty
) {}
