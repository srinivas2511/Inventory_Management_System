package com.mfg.ims.quality.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InspectionResultRequest(
        @NotNull @PositiveOrZero BigDecimal acceptedQty,
        @NotNull @PositiveOrZero BigDecimal rejectedQty,
        String remarks,
        @NotEmpty @Valid List<ItemResultRequest> items
) {
    public record ItemResultRequest(
            @NotNull Long itemId,
            BigDecimal actualValue,
            String actualText,
            String deviationNote
    ) {}
}
