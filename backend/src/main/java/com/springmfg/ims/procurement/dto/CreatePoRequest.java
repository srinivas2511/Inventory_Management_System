package com.springmfg.ims.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePoRequest(
        @NotNull Long supplierId,
        @NotNull LocalDate poDate,
        LocalDate expectedDeliveryDate,
        String paymentTerms,
        String remarks,
        Long requisitionId,
        @NotEmpty @Valid List<PoLineRequest> items
) {
    public record PoLineRequest(
            @NotNull Long materialId,
            @NotNull @Positive BigDecimal orderedQty,
            @NotNull @Positive BigDecimal rate,
            BigDecimal taxPct
    ) {}
}
