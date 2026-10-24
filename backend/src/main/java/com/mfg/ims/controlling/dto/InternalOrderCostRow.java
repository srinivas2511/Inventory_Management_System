package com.mfg.ims.controlling.dto;

import java.math.BigDecimal;

public record InternalOrderCostRow(
        Long glAccountId,
        String accountCode,
        String accountName,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal netAmount) {}
