package com.mfg.ims.procurement.dto;

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
        @NotEmpty @Valid List<PoLineRequest> items,
        Long plantId
) {
    /** Without a plant: the creating user's only plant, or the default plant. */
    public CreatePoRequest(Long supplierId, LocalDate poDate, LocalDate expectedDeliveryDate, String paymentTerms,
                           String remarks, Long requisitionId, List<PoLineRequest> items) {
        this(supplierId, poDate, expectedDeliveryDate, paymentTerms, remarks, requisitionId, items, null);
    }

    public record PoLineRequest(
            @NotNull Long materialId,
            @NotNull @Positive BigDecimal orderedQty,
            @NotNull @Positive BigDecimal rate,
            BigDecimal taxPct
    ) {}
}
