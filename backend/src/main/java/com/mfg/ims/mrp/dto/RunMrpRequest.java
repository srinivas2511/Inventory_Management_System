package com.mfg.ims.mrp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Both horizonDays and includeSafetyStock are optional. When ocId is provided the demand
 * comes from that Order Confirmation's lines instead of all open Sales Orders (13.4).
 */
public record RunMrpRequest(
        @Min(1) @Max(730) Integer horizonDays,
        Boolean includeSafetyStock,
        Long ocId) {}
