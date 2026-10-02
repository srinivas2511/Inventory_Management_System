package com.springmfg.ims.engineering.bom;

import java.math.BigDecimal;

public record BomItemDto(
        Long id,
        int lineNo,
        String itemKind,
        Long materialId,
        String materialCode,
        String materialName,
        Long operationId,
        String operationName,
        BigDecimal requiredQty,
        String uom,
        BigDecimal scrapPct,
        String instruction) {}
