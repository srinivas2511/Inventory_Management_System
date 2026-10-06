package com.springmfg.ims.quality.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.PositiveOrZero;

/** Optional quantities for accepting an incoming inspection; both together must equal the inspected quantity. */
public record AcceptInspectionRequest(
        @PositiveOrZero BigDecimal acceptedQty,
        @PositiveOrZero BigDecimal rejectedQty) {
}
