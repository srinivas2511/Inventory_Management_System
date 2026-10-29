package com.mfg.ims.production.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductionOperationDto(
        Long id,
        int sequenceNo,
        Long operationId,
        String operationName,
        Long machineId,
        String machineName,
        Long assignedOperatorId,
        String assignedOperatorName,
        boolean inspectionPoint,
        String status,
        BigDecimal inputQty,
        BigDecimal goodQty,
        BigDecimal rejectedQty,
        BigDecimal scrapQty,
        Instant startedAt,
        Instant completedAt,
        Instant scheduledStart,
        Instant scheduledEnd,
        Long productionOrderId,
        String productionOrderNumber,
        Long productId,
        boolean finalOperation,
        String startBlockedReason,
        Instant plannedStart,
        Instant plannedEnd) {}
