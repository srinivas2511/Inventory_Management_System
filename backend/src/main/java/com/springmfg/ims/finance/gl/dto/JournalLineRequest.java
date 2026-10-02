package com.springmfg.ims.finance.gl.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record JournalLineRequest(
        @NotNull Long glAccountId,
        @PositiveOrZero BigDecimal debitAmount,
        @PositiveOrZero BigDecimal creditAmount,
        String description
) {}
