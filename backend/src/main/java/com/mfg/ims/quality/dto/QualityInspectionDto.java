package com.mfg.ims.quality.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record QualityInspectionDto(
        Long id,
        String inspectionNumber,
        String inspectionType,
        String status,
        Long goodsReceiptItemId,
        String grNumber,
        Long materialBatchId,
        String batchNumber,
        Long materialId,
        String materialCode,
        Long fgBatchId,
        Long productionOrderId,
        Long productionOperationId,
        BigDecimal inspectedQty,
        Integer sampleSize,
        BigDecimal acceptedQty,
        BigDecimal rejectedQty,
        BigDecimal holdQty,
        Long inspectorId,
        Instant inspectedAt,
        Instant decisionAt,
        String remarks,
        List<InspectionItemDto> items
) {
    public record InspectionItemDto(
            Long id,
            Integer seq,
            String parameterName,
            String specification,
            BigDecimal lowerLimit,
            BigDecimal upperLimit,
            BigDecimal actualValue,
            String actualText,
            String unit,
            String result,
            String deviationNote,
            boolean mandatory,
            Long deviationApprovedBy,
            java.time.Instant deviationApprovedAt,
            String specSource
    ) {}
}
