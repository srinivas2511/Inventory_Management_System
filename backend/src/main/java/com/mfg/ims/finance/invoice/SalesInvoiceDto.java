package com.mfg.ims.finance.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SalesInvoiceDto(
        Long id,
        String docNo,
        LocalDate docDate,
        String status,
        String invoiceKind,
        Long customerId,
        String placeOfSupply,
        String supplyType,
        boolean reverseCharge,
        Long salesOrderId,
        Long ocId,
        Long dispatchId,
        String customerPoNo,
        LocalDate dueDate,
        String currency,
        BigDecimal exchangeRate,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        BigDecimal tcsAmount,
        BigDecimal grandTotal,
        BigDecimal balanceInr,
        String einvoiceStatus,
        String irn,
        String cancelledReason,
        Instant postedAt,
        String remarks,
        String invoiceType,
        String lutArn,
        List<ItemDto> items,
        java.util.Map<String, Object> packagingDetails,
        Long serviceConfirmationId
) {
    public record ItemDto(
            Long id,
            int lineNo,
            String itemKind,
            Long materialId,
            Long productId,
            String description,
            BigDecimal qty,
            String uom,
            BigDecimal rate,
            BigDecimal discountPct,
            BigDecimal taxableValue,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            String hsnSac
    ) {}
}
