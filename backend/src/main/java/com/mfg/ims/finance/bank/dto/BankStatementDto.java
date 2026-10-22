package com.mfg.ims.finance.bank.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankStatementDto(
        Long id,
        String statementNumber,
        Long glAccountId,
        LocalDate statementDate,
        Long periodId,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        String status,
        int totalLines,
        int matchedLines,
        int unmatchedLines
) {}
