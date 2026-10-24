package com.mfg.ims.finance.gl.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProfitLossStatement(
        Long periodId,
        String periodName,
        Integer fiscalYear,
        List<FinancialStatementRow> revenue,
        List<FinancialStatementRow> cogs,
        List<FinancialStatementRow> expenses,
        BigDecimal totalRevenue,
        BigDecimal totalCogs,
        BigDecimal grossProfit,
        BigDecimal totalExpenses,
        BigDecimal netIncome) {}
