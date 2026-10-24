package com.mfg.ims.controlling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateInternalOrderRequest(
        @NotBlank @Size(max = 20) String orderNumber,
        @NotBlank @Size(max = 200) String description,
        @NotNull String orderType,
        Long responsibleCostCentreId,
        @NotNull BigDecimal budgetAmount,
        LocalDate startDate,
        LocalDate endDate) {}
