package com.mfg.ims.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Running stock card for a single material (gap 13.6).
 */
public record StockCardDto(
        Long materialId,
        String materialCode,
        String materialName,
        List<StockCardLineDto> lines) {

    public record StockCardLineDto(
            Instant date,
            String reference,
            String transactionType,
            BigDecimal inQty,
            BigDecimal outQty,
            BigDecimal balance,
            String warehouseCode) {}
}
