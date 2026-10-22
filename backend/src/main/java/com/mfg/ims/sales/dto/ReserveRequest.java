package com.mfg.ims.sales.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReserveRequest(
        @NotNull Long salesOrderItemId,
        @NotNull Long fgBatchId,
        @NotNull Long warehouseId,
        @NotNull Long locationId,
        @NotNull @Positive BigDecimal qty) {}
