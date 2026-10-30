package com.mfg.ims.production.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QuickCreateRequest(
        @NotNull Long bomId,
        @NotNull @Positive BigDecimal qty,
        @NotNull Long warehouseId,
        boolean completeImmediately) {}
