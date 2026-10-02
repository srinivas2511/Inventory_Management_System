package com.springmfg.ims.engineering.bom;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record BomItemRequest(
        @Positive int lineNo,
        @NotBlank String itemKind,
        Long materialId,
        Long operationId,
        BigDecimal requiredQty,
        String uom,
        BigDecimal scrapPct,
        String instruction) {}
