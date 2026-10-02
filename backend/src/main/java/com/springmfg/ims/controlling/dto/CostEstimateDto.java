package com.springmfg.ims.controlling.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CostEstimateDto(
        Long id,
        String estimateNumber,
        Long productId,
        String productCode,
        String productName,
        Long bomId,
        String bomNumber,
        Long costCentreId,
        String status,
        Instant estimatedAt,
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal materialCost,
        BigDecimal labourCost,
        BigDecimal machineCost,
        BigDecimal overheadCost,
        BigDecimal totalStandardCost,
        BigDecimal costLotSize,
        BigDecimal unitStandardCost,
        Instant releasedAt,
        List<CostEstimateLineDto> lines,
        List<String> allowedActions
) {}
