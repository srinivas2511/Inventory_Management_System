package com.mfg.ims.inventory.dto;

import java.math.BigDecimal;

public record ValuationLineDto(
        String itemType,
        Long itemId,
        String itemCode,
        String itemName,
        String uom,
        BigDecimal qtyOnHand,
        BigDecimal avgUnitCost,
        BigDecimal standardCost,
        BigDecimal totalValue
) {}
