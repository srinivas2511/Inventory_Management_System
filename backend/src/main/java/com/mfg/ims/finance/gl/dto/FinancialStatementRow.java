package com.mfg.ims.finance.gl.dto;

import java.math.BigDecimal;

public record FinancialStatementRow(
        Long glAccountId,
        String accountCode,
        String accountName,
        String accountType,
        BigDecimal balance) {}
