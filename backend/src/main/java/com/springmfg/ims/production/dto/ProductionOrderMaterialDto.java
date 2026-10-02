package com.springmfg.ims.production.dto;

import java.math.BigDecimal;

public record ProductionOrderMaterialDto(
        Long id,
        Long materialId,
        String materialCode,
        String materialName,
        BigDecimal plannedQty,
        BigDecimal issuedQty,
        BigDecimal consumedQty,
        BigDecimal returnedQty,
        BigDecimal scrapQty,
        BigDecimal remainingQty,
        String uom,
        BigDecimal availableQty,
        BigDecimal shortage) {}
