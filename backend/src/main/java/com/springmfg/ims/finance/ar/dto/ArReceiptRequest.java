package com.springmfg.ims.finance.ar.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ArReceiptRequest(
        @NotNull LocalDate receiptDate,
        @NotNull @Positive BigDecimal amount,
        String paymentMode,
        String referenceNumber
) {}
