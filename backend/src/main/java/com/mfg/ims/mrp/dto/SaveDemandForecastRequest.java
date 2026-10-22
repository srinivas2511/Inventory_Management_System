package com.mfg.ims.mrp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SaveDemandForecastRequest(
        @NotNull Long productId,
        @NotNull LocalDate targetDate,
        @NotNull @Positive BigDecimal quantity,
        @Size(max = 300) String notes) {}
