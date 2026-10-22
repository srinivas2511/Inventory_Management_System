package com.mfg.ims.controlling.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record VarianceDto(
        Long id,
        Long productionOrderId,
        String orderNumber,
        Long productId,
        String productCode,
        String productName,
        Long estimateId,
        BigDecimal goodQty,
        BigDecimal standardCost,
        BigDecimal actualMaterialCost,
        BigDecimal actualLabourCost,
        BigDecimal actualMachineCost,
        BigDecimal actualOverheadCost,
        BigDecimal actualCost,
        BigDecimal priceVariance,
        BigDecimal quantityVariance,
        BigDecimal efficiencyVariance,
        BigDecimal otherVariance,
        BigDecimal totalVariance,
        BigDecimal variancePct,
        String status,
        Long journalHeaderId,
        Instant calculatedAt
) {}
