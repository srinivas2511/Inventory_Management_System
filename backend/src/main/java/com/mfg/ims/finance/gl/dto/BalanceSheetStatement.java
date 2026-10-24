package com.mfg.ims.finance.gl.dto;

import java.math.BigDecimal;
import java.util.List;

public record BalanceSheetStatement(
        Long periodId,
        String periodName,
        List<FinancialStatementRow> assets,
        List<FinancialStatementRow> liabilities,
        List<FinancialStatementRow> equity,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal retainedEarnings,
        BigDecimal totalEquity,
        BigDecimal totalLiabilitiesAndEquity) {}
