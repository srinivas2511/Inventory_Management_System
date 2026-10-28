package com.mfg.ims.sales.orderconfirmation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveOcRequest(
        @NotNull Long customerId,
        LocalDate docDate,
        String ocKind,
        Long quotationId,
        String customerPoNo,
        LocalDate customerPoDate,
        LocalDate deliveryDate,
        String currency,
        String incoterm,
        String remarks,
        @Size(min = 1) List<ItemLine> items
) {
    public record ItemLine(
            Long materialId,
            Long productId,
            String description,
            @NotNull BigDecimal qty,
            String uom,
            BigDecimal rate,
            LocalDate deliveryDate,
            String customerPartNo
    ) {}
}
