package com.springmfg.ims.controlling.dto;

import jakarta.validation.constraints.NotNull;

public record CreateCostEstimateRequest(
        @NotNull Long productId,
        @NotNull Long bomId,
        Long costCentreId
) {}
