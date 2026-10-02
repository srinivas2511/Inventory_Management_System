package com.springmfg.ims.controlling.dto;

import java.math.BigDecimal;

public record CostCentreBudgetDto(
        Long id,
        Long costCentreId,
        Long glAccountId,
        Long periodId,
        BigDecimal budgetedAmount
) {}
