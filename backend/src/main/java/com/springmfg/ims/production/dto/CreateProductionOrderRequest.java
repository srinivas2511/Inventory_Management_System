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
        String remarks,
        Long plantId) {

    /** Without a plant: the creating user's only plant, or the default plant. */
    public CreateProductionOrderRequest(Long productId, Long bomId, BigDecimal plannedQty, LocalDate startDate,
                                        LocalDate expectedCompletionDate, String productionLine, Long primaryMachineId,
                                        Long supervisorId, Long salesOrderId, Short priority, String remarks) {
        this(productId, bomId, plannedQty, startDate, expectedCompletionDate, productionLine, primaryMachineId,
                supervisorId, salesOrderId, priority, remarks, null);
    }
}
