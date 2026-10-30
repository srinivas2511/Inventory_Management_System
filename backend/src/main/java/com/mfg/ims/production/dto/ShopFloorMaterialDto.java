package com.mfg.ims.production.dto;

import java.math.BigDecimal;

public record ShopFloorMaterialDto(
        Long materialId,
        String materialCode,
        String materialName,
        String uom,
        BigDecimal plannedQty,
        BigDecimal issuedQty,
        BigDecimal remainingQty) {}
