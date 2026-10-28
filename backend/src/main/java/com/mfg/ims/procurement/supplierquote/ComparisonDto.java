package com.mfg.ims.procurement.supplierquote;

import java.math.BigDecimal;
import java.util.List;

public record ComparisonDto(Long rfqId, List<ComparisonLineDto> lines) {

    public record ComparisonLineDto(
            Long rfqItemId,
            int lineNo,
            String description,
            BigDecimal requiredQty,
            List<SupplierBidDto> bids) {}

    public record SupplierBidDto(
            Long quoteId,
            Long supplierId,
            BigDecimal unitPrice,
            BigDecimal taxPct,
            BigDecimal freight,
            Integer leadTimeDays,
            BigDecimal landedCost,
            boolean lowest) {}
}
