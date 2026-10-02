package com.springmfg.ims.finance.config.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record BudgetRequest(
        @NotNull Long glAccountId,
        @NotNull Integer fiscalYear,
        @NotNull Short periodNumber,
        @NotNull @PositiveOrZero BigDecimal budgetedAmount,
        String costCenter
) {}
