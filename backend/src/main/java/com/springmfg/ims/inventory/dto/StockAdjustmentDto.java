package com.springmfg.ims.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.springmfg.ims.inventory.domain.StockAdjustment;

public record StockAdjustmentDto(
        Long id,
        String adjustmentNumber,
        String itemType,
        Long materialId,
        Long productId,
        Long batchId,
        Long warehouseId,
        Long locationId,
        String stockStatus,
        BigDecimal systemQty,
        BigDecimal countedQty,
        BigDecimal varianceQty,
        BigDecimal varianceValue,
        String reason,
        String status,
        Long requestedBy,
        Long postedTransactionId,
        Instant createdAt,
        Long version) {

    public static StockAdjustmentDto from(StockAdjustment a) {
        return new StockAdjustmentDto(
                a.getId(), a.getAdjustmentNumber(), a.getItemType(),
                a.getMaterialId(), a.getProductId(), a.getBatchId(),
                a.getWarehouseId(), a.getLocationId(), a.getStockStatus(),
                a.getSystemQty(), a.getCountedQty(), a.varianceQty(),
                a.getVarianceValue(), a.getReason(), a.getStatus(),
                a.getRequestedBy(), a.getPostedTransactionId(),
                a.getCreatedAt(), a.getVersion());
    }
}
