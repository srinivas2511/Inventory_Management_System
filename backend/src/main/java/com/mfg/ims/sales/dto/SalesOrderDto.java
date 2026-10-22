package com.mfg.ims.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderDto(
        Long id,
        String orderNumber,
        Long customerId,
        String customerName,
        LocalDate orderDate,
        LocalDate requiredDate,
        String deliveryAddress,
        String paymentTerms,
        String notes,
        String status,
        BigDecimal totalValue,
        List<SalesOrderItemDto> items,
        Long createdBy,
        Long submittedBy,
        List<String> allowedActions) {

    public record SalesOrderItemDto(
            Long id,
            int lineNo,
            Long productId,
            String productCode,
            String productName,
            BigDecimal orderedQty,
            BigDecimal reservedQty,
            BigDecimal packedQty,
            BigDecimal dispatchedQty,
            BigDecimal pendingQty,
            BigDecimal unitPrice,
            String customerSpecRef,
            String notes,
            String fulfillmentStatus) {}
}
