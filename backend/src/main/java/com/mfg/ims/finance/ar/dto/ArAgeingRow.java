package com.mfg.ims.finance.ar.dto;

import java.math.BigDecimal;

public record ArAgeingRow(
        Long customerId,
        String customerName,
        BigDecimal current,
        BigDecimal overdue30,
        BigDecimal overdue60,
        BigDecimal overdue90,
        BigDecimal overdue90Plus,
        BigDecimal total
) {}
