package com.mfg.ims.finance.openitem;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OpenItemDto(
        Long id,
        String itemType,
        String partyType,
        Long partyId,
        String sourceType,
        Long sourceId,
        String docNo,
        LocalDate docDate,
        LocalDate dueDate,
        String currency,
        BigDecimal amount,
        BigDecimal amountInr,
        BigDecimal balance,
        BigDecimal balanceInr,
        String ageBucket
) {}
