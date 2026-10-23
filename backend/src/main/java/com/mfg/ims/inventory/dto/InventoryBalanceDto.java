package com.mfg.ims.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.mfg.ims.inventory.domain.InventoryBalance;

public record InventoryBalanceDto(
        Long id,
        Long warehouseId,
        String warehouseName,
        Long locationId,
        String locationName,
        String itemType,
        Long materialId,
        Long productId,
        Long materialBatchId,
        Long fgBatchId,
        String stockStatus,
        BigDecimal qtyOnHand,
        BigDecimal qtyReserved,
        BigDecimal qtyAvailable,
        BigDecimal avgUnitCost,
        BigDecimal totalValue,
        Instant updatedAt) {

    public static InventoryBalanceDto from(InventoryBalance b, String warehouseName, String locationName) {
        return new InventoryBalanceDto(
                b.getId(), b.getWarehouseId(), warehouseName, b.getLocationId(), locationName,
                b.getItemType(), b.getMaterialId(), b.getProductId(),
                b.getMaterialBatchId(), b.getFgBatchId(), b.getStockStatus(),
                b.getQtyOnHand(), b.getQtyReserved(), b.available(),
                b.getAvgUnitCost(),
                b.getQtyOnHand().multiply(b.getAvgUnitCost()),
                b.getUpdatedAt());
    }
}
