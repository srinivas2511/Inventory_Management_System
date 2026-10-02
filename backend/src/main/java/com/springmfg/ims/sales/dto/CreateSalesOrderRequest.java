package com.springmfg.ims.sales.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateSalesOrderRequest(
        @NotNull Long customerId,
        @NotNull LocalDate orderDate,
        LocalDate requiredDate,
        String deliveryAddress,
        String paymentTerms,
        String notes,
        @Valid List<AddSalesOrderItemRequest> items) {}
