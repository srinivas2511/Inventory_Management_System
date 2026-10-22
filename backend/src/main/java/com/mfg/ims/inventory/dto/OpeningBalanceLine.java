package com.mfg.ims.inventory.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OpeningBalanceLine(
        @NotBlank String itemType,
        Long materialId,
        Long productId,
        Long batchId,
        @NotNull Long warehouseId,
        @NotNull Long locationId,
        @NotBlank String stockStatus,
        @NotNull @Positive BigDecimal quantity,
        @NotBlank String uom,
        BigDecimal unitCost,
        String remarks) {
}
