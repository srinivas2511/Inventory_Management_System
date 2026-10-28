package com.mfg.ims.procurement.supplierquote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SupplierQuoteDto(
        Long id,
        String docNo,
        String status,
        Long rfqId,
        Long supplierId,
        LocalDate validUntil,
        Integer leadTimeDays,
        BigDecimal freight,
        String notes,
        boolean awarded,
        List<ItemDto> items) {

    public record ItemDto(
            Long id,
            int lineNo,
            Long rfqItemId,
            BigDecimal unitPrice,
            BigDecimal taxPct,
            BigDecimal moq,
            BigDecimal awardedQty) {}
}
