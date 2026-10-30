package com.mfg.ims.finance.advance;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdvanceReceiptRequest(
        @NotNull Long customerId,
        @NotNull LocalDate receiptDate,
        @NotNull @Positive BigDecimal amount,
        String currencyCode,
        String reference,
        String notes
) {}
