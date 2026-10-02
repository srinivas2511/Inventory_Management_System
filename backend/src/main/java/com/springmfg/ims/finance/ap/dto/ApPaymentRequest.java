package com.springmfg.ims.finance.ap.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ApPaymentRequest(
        @NotNull LocalDate paymentDate,
        @NotNull @Positive BigDecimal amount,
        String paymentMode,
        String referenceNumber
) {}
