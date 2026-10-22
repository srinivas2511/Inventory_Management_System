package com.mfg.ims.inventory.dto;

import java.math.BigDecimal;

/** MDM-003 / GC-1: a stock balance of a material shown in an alternative unit beside its base-unit quantity. */
public record ConvertedBalanceDto(
        Long warehouseId,
        Long locationId,
        Long materialBatchId,
        String stockStatus,
        String baseUom,
        BigDecimal baseOnHand,
        BigDecimal baseAvailable,
        String uom,
        BigDecimal onHand,
        BigDecimal available) {
}
