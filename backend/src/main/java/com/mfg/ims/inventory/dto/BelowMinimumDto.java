package com.mfg.ims.inventory.dto;

import java.math.BigDecimal;

/** A material whose current AVAILABLE stock is below its minimum level. */
public record BelowMinimumDto(
        Long materialId,
        String materialCode,
        String name,
        BigDecimal minStock,
        BigDecimal onHand,
        String uom,
        BigDecimal shortage) {
}
