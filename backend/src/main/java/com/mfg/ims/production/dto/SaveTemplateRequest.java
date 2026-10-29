package com.mfg.ims.production.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SaveTemplateRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull Long productId,
        Long bomId,
        BigDecimal plannedQty,
        Integer leadTimeDays,
        String notes
) {}
