package com.springmfg.ims.mrp.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Exactly one of materialId / productId is set. planningStrategy MTO is meaningful for products only. */
public record SavePlanningParamRequest(
        Long materialId,
        Long productId,
        @NotNull @Pattern(regexp = "MTS|MTO") String planningStrategy,
        @NotNull @Pattern(regexp = "EXACT|FIXED|MIN_MAX|EOQ") String lotSizeRule,
        @Positive BigDecimal fixedLotQty,
        @Min(0) BigDecimal minLotQty,
        @Positive BigDecimal maxLotQty,
        @Positive BigDecimal eoqAnnualDemand,
        @Positive BigDecimal eoqOrderCost,
        @Positive BigDecimal eoqHoldingCost,
        @Min(0) Integer leadTimeDays) {}
