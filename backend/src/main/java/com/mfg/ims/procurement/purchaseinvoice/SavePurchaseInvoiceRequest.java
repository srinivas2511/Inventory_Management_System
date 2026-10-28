package com.mfg.ims.procurement.purchaseinvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SavePurchaseInvoiceRequest(
        @NotBlank String docNo,
        @NotNull Long supplierId,
        @NotBlank String supplierInvoiceNo,
        @NotNull LocalDate supplierInvoiceDate,
        Long poId,
        LocalDate dueDate,
        @Valid List<ItemLine> items) {

    public record ItemLine(
            Long poItemId,
            Long grnItemId,
            Long materialId,
            Long productId,
            String description,
            @NotNull BigDecimal qty,
            @NotNull BigDecimal unitPrice,
            BigDecimal discountPct,
            BigDecimal cgstRate,
            BigDecimal sgstRate,
            BigDecimal igstRate) {}
}
