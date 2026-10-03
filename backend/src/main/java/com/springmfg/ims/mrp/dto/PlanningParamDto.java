package com.springmfg.ims.mrp.dto;

import java.math.BigDecimal;

public record PlanningParamDto(
        Long id,
        String itemType,
        Long materialId,
        Long productId,
        String itemCode,
        String itemName,
        String planningStrategy,
        String lotSizeRule,
        BigDecimal fixedLotQty,
        BigDecimal minLotQty,
        BigDecimal maxLotQty,
        BigDecimal eoqAnnualDemand,
        BigDecimal eoqOrderCost,
        BigDecimal eoqHoldingCost,
        Integer leadTimeDays) {}
