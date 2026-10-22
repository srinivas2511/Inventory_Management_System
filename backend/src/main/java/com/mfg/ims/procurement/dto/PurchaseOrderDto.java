package com.mfg.ims.procurement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderDto(
        Long id,
        String poNumber,
        Long supplierId,
        String supplierName,
        LocalDate poDate,
        LocalDate expectedDeliveryDate,
        String status,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        String currency,
        String paymentTerms,
        String remarks,
        Long requisitionId,
        Long submittedBy,
        Long approvedBy,
        Instant approvedAt,
        List<PurchaseOrderItemDto> items,
        List<String> allowedActions
) {
    public record PurchaseOrderItemDto(
            Long id,
            Integer lineNo,
            Long materialId,
            String materialCode,
            String materialName,
            String uom,
            BigDecimal orderedQty,
            BigDecimal receivedQty,
            BigDecimal acceptedQty,
            BigDecimal rejectedQty,
            BigDecimal rate,
            BigDecimal taxPct,
            BigDecimal lineTotal
    ) {}
}
