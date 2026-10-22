package com.mfg.ims.finance.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record FinanceDashboardDto(
        String openPeriodName,
        BigDecimal totalApOutstanding,
        BigDecimal totalArOutstanding,
        BigDecimal apOverdue,
        BigDecimal arOverdue,
        List<RevenueVsBudgetItem> revenueVsBudget
) {
    public record RevenueVsBudgetItem(String period, BigDecimal actual, BigDecimal budget) {}
}
