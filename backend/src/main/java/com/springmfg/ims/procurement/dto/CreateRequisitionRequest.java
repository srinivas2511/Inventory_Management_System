package com.springmfg.ims.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateRequisitionRequest(
        @NotNull Long materialId,
        @NotNull @Positive BigDecimal requestedQty,
        @NotNull String uom,
        LocalDate neededBy,
        String reason
) {}
