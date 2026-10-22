package com.mfg.ims.sales.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddSalesOrderItemRequest(
        @NotNull Long productId,
        @NotNull @Positive BigDecimal orderedQty,
        BigDecimal unitPrice,
        String customerSpecRef,
        String notes) {}
