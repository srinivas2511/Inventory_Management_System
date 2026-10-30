package com.mfg.ims.finance.advance;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ApplyAdvanceRequest(
        @NotNull Long invoiceId,
        @NotNull @Positive BigDecimal amount
) {}
