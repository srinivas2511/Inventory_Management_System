package com.mfg.ims.finance.payment;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SavePaymentRequest(
        @NotNull String direction,
        String kind,
        @NotNull String partyType,
        @NotNull Long partyId,
        @NotNull String mode,
        String referenceNo,
        LocalDate docDate,
        String currency,
        @NotNull BigDecimal amount,
        BigDecimal tdsAmount,
        BigDecimal bankCharges,
        String remarks
) {}
