package com.springmfg.ims.inventory.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
        @NotBlank String itemType,
        Long materialId,
        Long productId,
        Long materialBatchId,
        Long fgBatchId,
        @NotNull Long fromWarehouseId,
        @NotNull Long fromLocationId,
        @NotBlank String fromStockStatus,
        @NotNull Long toWarehouseId,
        @NotNull Long toLocationId,
        @NotBlank String toStockStatus,
        @NotNull @Positive BigDecimal quantity,
        @NotBlank String uom,
        String referenceType,
        Long referenceId,
        String referenceNumber,
        String remarks) {
}
