package com.springmfg.ims.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateProductionOrderRequest(
        @NotNull Long productId,
        @NotNull Long bomId,
        @NotNull @Positive BigDecimal plannedQty,
        LocalDate startDate,
        LocalDate expectedCompletionDate,
        String productionLine,
        Long primaryMachineId,
        Long supervisorId,
        Long salesOrderId,
        Short priority,
        String remarks) {}
