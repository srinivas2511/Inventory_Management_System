package com.mfg.ims.production.jobwork;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateJobWorkOrderRequest(
        LocalDate docDate,
        @NotNull Long vendorId,
        String challanNo,
        LocalDate issueDate,
        LocalDate expectedReturn,
        BigDecimal jobCharges,
        String remarks,
        List<ItemLine> items
) {
    public record ItemLine(
            Long materialId,
            Long productId,
            String description,
            BigDecimal sentQty,
            String uom
    ) {}
}
