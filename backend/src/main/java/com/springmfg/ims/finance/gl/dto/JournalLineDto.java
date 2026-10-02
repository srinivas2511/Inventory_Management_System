package com.springmfg.ims.finance.gl.dto;

import java.math.BigDecimal;

public record JournalLineDto(
        Long id,
        Integer lineNumber,
        Long glAccountId,
        String accountCode,
        String accountName,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String description,
        Long costCentreId,
        Long profitCentreId
) {}
