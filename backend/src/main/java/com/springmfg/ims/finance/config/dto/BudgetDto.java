package com.springmfg.ims.finance.config.dto;

import java.math.BigDecimal;

public record BudgetDto(
        Long id,
        Long glAccountId,
        String accountCode,
        String accountName,
        Integer fiscalYear,
        Short periodNumber,
        BigDecimal budgetedAmount,
        String costCenter
) {}
