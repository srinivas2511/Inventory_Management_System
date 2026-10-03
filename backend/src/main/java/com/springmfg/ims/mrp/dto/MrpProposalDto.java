package com.springmfg.ims.mrp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MrpProposalDto(
        Long id,
        Long mrpRunId,
        String proposalType,
        Long materialId,
        String materialCode,
        String materialName,
        Long productId,
        String productCode,
        String productName,
        Long bomId,
        String uom,
        BigDecimal requiredQty,
        BigDecimal proposedQty,
        LocalDate needDate,
        LocalDate proposedOrderDate,
        int leadTimeDays,
        boolean leadTimeAdjusted,
        String sourceDemandType,
        Long sourceDemandId,
        String lotSizeRule,
        BigDecimal machineHoursRequired,
        boolean capacityOverload,
        String status,
        String convertedDocumentType,
        Long convertedDocumentId,
        String discardReason) {}
