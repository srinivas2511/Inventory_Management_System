package com.springmfg.ims.production.dto;

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
        boolean inspectionPoint,
        String status,
        BigDecimal inputQty,
        BigDecimal goodQty,
        BigDecimal rejectedQty,
        BigDecimal scrapQty,
        Instant startedAt,
        Instant completedAt) {}
