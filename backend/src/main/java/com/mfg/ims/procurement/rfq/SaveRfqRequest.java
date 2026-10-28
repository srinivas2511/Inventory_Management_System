package com.mfg.ims.procurement.rfq;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaveRfqRequest(
        @NotBlank String docNo,
        LocalDate dueDate,
        String deliveryLocation,
        String notes,
        @NotEmpty @Valid List<ItemLine> items) {

    public record ItemLine(
            Long materialId,
            Long productId,
            String description,
            @NotNull @Positive BigDecimal qty,
            Long uomId,
            LocalDate requiredDate) {}
}
