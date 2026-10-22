package com.mfg.ims.inventory.dto;

import java.math.BigDecimal;

public record TransferCommand(
        String itemType,
        Long materialId,
        Long productId,
        Long materialBatchId,
        Long fgBatchId,
        Long fromWarehouseId,
        Long fromLocationId,
        String fromStockStatus,
        Long toWarehouseId,
        Long toLocationId,
        String toStockStatus,
        BigDecimal quantity,
        String uom,
        String referenceType,
        Long referenceId,
        String referenceNumber,
        String remarks) {
}
