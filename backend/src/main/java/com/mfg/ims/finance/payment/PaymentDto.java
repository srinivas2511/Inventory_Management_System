package com.mfg.ims.finance.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PaymentDto(
        Long id,
        String docNo,
        LocalDate docDate,
        String status,
        String direction,
        String kind,
        String partyType,
        Long partyId,
        String mode,
        String referenceNo,
        String currency,
        BigDecimal exchangeRate,
        BigDecimal amount,
        BigDecimal amountInr,
        BigDecimal tdsAmount,
        BigDecimal bankCharges,
        BigDecimal unallocatedInr,
        String remarks,
        List<AllocationDto> allocations
) {
    public record AllocationDto(
            Long id,
            Long openItemId,
            BigDecimal allocated,
            BigDecimal allocatedInr,
            Instant allocatedAt
    ) {}
}
