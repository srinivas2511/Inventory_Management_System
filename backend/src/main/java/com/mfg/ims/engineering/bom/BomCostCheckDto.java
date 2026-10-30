package com.mfg.ims.engineering.bom;

import java.math.BigDecimal;
import java.util.List;

public record BomCostCheckDto(
        Long bomId,
        BigDecimal totalEstimatedCost,
        List<CostLine> lines) {

    public record CostLine(Long materialId, String name, BigDecimal qty, BigDecimal unitCost, BigDecimal lineCost) {}
}
