package com.springmfg.ims.controlling.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SaveCostCentreRequest(
        @NotBlank @Size(max = 20) String costCentreCode,
        @NotBlank @Size(max = 100) String name,
        Long parentId,
        Long responsibleUserId,
        Long plantId,
        @NotNull @PositiveOrZero BigDecimal labourRatePerHour,
        @NotNull @PositiveOrZero BigDecimal machineRatePerHour,
        @NotNull @PositiveOrZero BigDecimal overheadPct
) {}
