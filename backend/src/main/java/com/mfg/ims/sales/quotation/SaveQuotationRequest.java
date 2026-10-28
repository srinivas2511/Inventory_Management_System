package com.mfg.ims.sales.quotation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveQuotationRequest(
        @NotNull Long customerId,
        LocalDate docDate,
        LocalDate validUntil,
        String currency,
        String deliveryTerms,
        String enquiryRef,
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
            BigDecimal discountPct,
            Integer leadTimeDays,
            String customerPartNo
    ) {}
}
