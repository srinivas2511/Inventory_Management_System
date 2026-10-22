package com.mfg.ims.production.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ProductionOrderDto(
        Long id,
        String orderNumber,
        Long productId,
        String productCode,
        String productName,
        Long bomId,
        String bomNumber,
        BigDecimal plannedQty,
        BigDecimal goodQty,
        BigDecimal rejectedQty,
        BigDecimal scrapQty,
        LocalDate startDate,
        LocalDate expectedCompletionDate,
        Instant actualStartAt,
        Instant actualCompletionAt,
        String productionLine,
        Long supervisorId,
        String status,
        int priority,
        Instant releasedAt,
        Instant closedAt,
        String remarks,
        List<ProductionOrderMaterialDto> materials,
        List<ProductionOperationDto> operations,
        List<String> allowedActions) {}
