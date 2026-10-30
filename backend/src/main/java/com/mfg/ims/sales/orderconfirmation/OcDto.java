package com.mfg.ims.sales.orderconfirmation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OcDto(
        Long id,
        String docNo,
        LocalDate docDate,
        String status,
        String ocKind,
        Long customerId,
        Long quotationId,
        String customerPoNo,
        LocalDate customerPoDate,
        LocalDate deliveryDate,
        String currency,
        BigDecimal exchangeRate,
        BigDecimal grandTotal,
        String remarks,
        Long salesOrderId,
        Long submittedBy,
        Long originalOcId,
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
            LocalDate deliveryDate,
            String customerPartNo,
            BigDecimal qtyDispatched
    ) {}
}
