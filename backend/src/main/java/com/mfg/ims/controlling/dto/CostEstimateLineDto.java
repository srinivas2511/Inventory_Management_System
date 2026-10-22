package com.mfg.ims.controlling.dto;

import java.math.BigDecimal;

public record CostEstimateLineDto(
        Long id,
        String costElement,
        String itemDescription,
        Long materialId,
        Long operationId,
        BigDecimal quantity,
        String uom,
        BigDecimal unitRate,
        BigDecimal amount
) {}
