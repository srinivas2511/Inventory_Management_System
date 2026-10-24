package com.mfg.ims.finance.dashboard;

import java.math.BigDecimal;

public record RevenueVsBudgetItem(String period, BigDecimal actual, BigDecimal budget) {}
