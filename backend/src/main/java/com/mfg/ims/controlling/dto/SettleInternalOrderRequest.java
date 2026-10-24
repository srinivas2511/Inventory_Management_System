package com.mfg.ims.controlling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SettleInternalOrderRequest(
        @NotNull Long settlementCostCentreId,
        @NotBlank String reason) {}
