package com.mfg.ims.finance.gl.dto;

import java.math.BigDecimal;

public record TrialBalanceRow(
        Long glAccountId,
        String accountCode,
        String accountName,
        String accountType,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal balance
) {}
