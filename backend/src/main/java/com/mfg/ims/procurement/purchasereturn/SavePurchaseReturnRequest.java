package com.mfg.ims.procurement.purchasereturn;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SavePurchaseReturnRequest(
        @NotBlank String docNo,
        @NotNull Long supplierId,
        Long grnId,
        Long invoiceId,
        String reasonCode,
        String notes,
        @Valid List<ItemLine> items) {

    public record ItemLine(
            Long materialId,
            Long productId,
            String description,
            @NotNull @Positive BigDecimal qty,
            Long uomId,
            BigDecimal unitPrice,
            Long batchId,
            @NotNull Long locationId) {}
}
