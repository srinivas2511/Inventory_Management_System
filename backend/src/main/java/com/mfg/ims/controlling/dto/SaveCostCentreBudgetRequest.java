package com.mfg.ims.controlling.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SaveCostCentreBudgetRequest(
        @NotNull Long glAccountId,
        @NotNull Long periodId,
        @NotNull @PositiveOrZero BigDecimal budgetedAmount
) {}
