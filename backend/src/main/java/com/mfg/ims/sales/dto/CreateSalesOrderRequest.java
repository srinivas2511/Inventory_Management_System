package com.mfg.ims.sales.dto;

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
        @Valid List<AddSalesOrderItemRequest> items,
        Long plantId) {

    /** Without a plant: the creating user's only plant, or the default plant. */
    public CreateSalesOrderRequest(Long customerId, LocalDate orderDate, LocalDate requiredDate, String deliveryAddress,
                                   String paymentTerms, String notes, List<AddSalesOrderItemRequest> items) {
        this(customerId, orderDate, requiredDate, deliveryAddress, paymentTerms, notes, items, null);
    }
}
