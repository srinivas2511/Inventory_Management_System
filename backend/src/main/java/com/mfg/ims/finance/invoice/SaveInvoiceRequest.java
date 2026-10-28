package com.mfg.ims.finance.invoice;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SaveInvoiceRequest(
        @NotNull Long customerId,
        LocalDate docDate,
        String invoiceKind,
        String supplyType,
        String placeOfSupply,
        Long ocId,
        Long salesOrderId,
        Long dispatchId,
        String customerPoNo,
        LocalDate dueDate,
        String currency,
        String remarks,
        List<ItemLine> items
) {
    public record ItemLine(
            Long productId,
            Long materialId,
            String description,
            BigDecimal qty,
            String uom,
            BigDecimal rate,
            BigDecimal discountPct,
            String hsnSac
    ) {}
}
