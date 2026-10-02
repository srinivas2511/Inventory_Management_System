package com.springmfg.ims.quality.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FgBatchDto(
        Long id,
        String batchNumber,
        Long productId,
        String productCode,
        String productName,
        Long productionOrderId,
        BigDecimal producedQty,
        BigDecimal approvedQty,
        BigDecimal rejectedQty,
        BigDecimal remainingQty,
        LocalDate producedOn,
        String qualityStatus,
        BigDecimal unitCost,
        Long pendingInspectionId
) {}
