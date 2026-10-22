package com.mfg.ims.mrp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Both fields are optional: the horizon defaults to setting mrp.default_horizon_days, safety stock to true. */
public record RunMrpRequest(
        @Min(1) @Max(730) Integer horizonDays,
        Boolean includeSafetyStock) {}
