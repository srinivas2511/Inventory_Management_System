package com.springmfg.ims.masterdata.operation;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SaveOperationRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        String defaultMachineType,
        @NotNull @PositiveOrZero BigDecimal standardTimeMin,
        @NotNull @PositiveOrZero BigDecimal setupTimeMin,
        boolean isInspection) {}
