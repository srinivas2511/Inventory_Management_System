package com.mfg.ims.procurement.purchasereturnchallan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SaveChallanRequest(
        @NotNull Long supplierId,
        LocalDate docDate,
        Long purchaseReturnId,
        String vehicleNo,
        String lrNo,
        String remarks,
        @NotEmpty List<ItemRequest> items
) {
    public record ItemRequest(
            @NotNull Long materialId,
            @NotNull BigDecimal qty,
            String uom,
            BigDecimal unitPrice
    ) {}
}
