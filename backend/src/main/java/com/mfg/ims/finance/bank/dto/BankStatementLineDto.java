package com.mfg.ims.finance.bank.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankStatementLineDto(
        Long id,
        Long bankStatementId,
        LocalDate valueDate,
        String description,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String referenceNumber,
        Long matchedJournalLineId,
        String matchStatus
) {}
