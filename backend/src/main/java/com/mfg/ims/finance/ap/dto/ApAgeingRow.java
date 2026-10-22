package com.mfg.ims.finance.ap.dto;

import java.math.BigDecimal;

public record ApAgeingRow(
        Long supplierId,
        String supplierName,
        BigDecimal current,
        BigDecimal overdue30,
        BigDecimal overdue60,
        BigDecimal overdue90,
        BigDecimal overdue90Plus,
        BigDecimal total
) {}
