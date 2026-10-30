package com.mfg.ims.finance.advance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdvanceReceiptDto(
        Long id,
        Long customerId,
        LocalDate receiptDate,
        BigDecimal amount,
        String currencyCode,
        String reference,
        String notes,
        String status,
        BigDecimal appliedAmount,
        Long journalId
) {}
