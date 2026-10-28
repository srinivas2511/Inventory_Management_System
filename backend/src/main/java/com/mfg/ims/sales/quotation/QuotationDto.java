package com.mfg.ims.sales.quotation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record QuotationDto(
        Long id,
        String docNo,
        LocalDate docDate,
        String status,
        int revisionNo,
        Long customerId,
        String currency,
        LocalDate validUntil,
        BigDecimal grandTotal,
        String remarks,
        List<ItemDto> items
) {
    public record ItemDto(
            Long id,
            int lineNo,
            String itemKind,
            Long materialId,
            Long productId,
            String description,
            BigDecimal qty,
            String uom,
            BigDecimal rate,
            BigDecimal lineTotal,
            Integer leadTimeDays,
            String customerPartNo
    ) {}
}
