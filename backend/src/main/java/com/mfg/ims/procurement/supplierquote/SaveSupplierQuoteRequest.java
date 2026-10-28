package com.mfg.ims.procurement.supplierquote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveSupplierQuoteRequest(
        @NotBlank String docNo,
        @NotNull Long rfqId,
        @NotNull Long supplierId,
        LocalDate validUntil,
        Integer leadTimeDays,
        BigDecimal freight,
        String notes,
        @Valid List<ItemLine> items) {

    public record ItemLine(
            @NotNull Long rfqItemId,
            int lineNo,
            BigDecimal unitPrice,
            BigDecimal taxPct,
            BigDecimal moq) {}
}
