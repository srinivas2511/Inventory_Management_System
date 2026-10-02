package com.springmfg.ims.production.dto;

import java.math.BigDecimal;
import java.util.List;

public record IssueDto(
        Long id,
        String issueNumber,
        Long productionOrderId,
        List<IssueLineDto> lines) {

    public record IssueLineDto(
            Long materialId,
            Long materialBatchId,
            String batchNumber,
            Long fromLocationId,
            BigDecimal quantity,
            BigDecimal unitCost) {}
}
