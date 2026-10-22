package com.mfg.ims.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.mfg.ims.inventory.domain.InventoryTransaction;

public record InventoryTransactionDto(
        Long id,
        String transactionNumber,
        Instant transactionDate,
        String transactionType,
        String itemType,
        Long materialId,
        Long productId,
        Long materialBatchId,
        Long fgBatchId,
        Long warehouseId,
        Long locationId,
        String stockStatus,
        BigDecimal quantity,
        String uom,
        BigDecimal unitCost,
        BigDecimal totalCost,
        String referenceType,
        Long referenceId,
        String referenceNumber,
        Long transferGroupId,
        Long reversalOfId,
        BigDecimal balanceAfter,
        String remarks,
        Instant createdAt) {

    public static InventoryTransactionDto from(InventoryTransaction t) {
        return new InventoryTransactionDto(
                t.getId(), t.getTransactionNumber(), t.getTransactionDate(),
                t.getTransactionType(), t.getItemType(),
                t.getMaterialId(), t.getProductId(),
                t.getMaterialBatchId(), t.getFgBatchId(),
                t.getWarehouseId(), t.getLocationId(), t.getStockStatus(),
                t.getQuantity(), t.getUom(), t.getUnitCost(), t.getTotalCost(),
                t.getReferenceType(), t.getReferenceId(), t.getReferenceNumber(),
                t.getTransferGroupId(), t.getReversalOfId(),
                t.getBalanceAfter(), t.getRemarks(), t.getCreatedAt());
    }
}
