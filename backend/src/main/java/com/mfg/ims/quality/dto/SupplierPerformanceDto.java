package com.mfg.ims.quality.dto;

import java.math.BigDecimal;
import java.util.List;

public record SupplierPerformanceDto(
        Long supplierId,
        String supplierName,
        List<MaterialAcceptance> byMaterial
) {
    public record MaterialAcceptance(
            Long materialId,
            String materialCode,
            BigDecimal received,
            BigDecimal accepted,
            BigDecimal rejected,
            BigDecimal acceptanceRate
    ) {}
}
