package com.mfg.ims.crm.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record QuotationDto(
        Long id,
        String quotationNumber,
        Long opportunityId,
        Long customerId,
        String customerName,
        LocalDate validUntil,
        String status,
        BigDecimal totalAmount,
        String paymentTerms,
        String notes,
        Instant sentAt,
        Instant respondedAt,
        String rejectReason,
        Long convertedSalesOrderId,
        String convertedSalesOrderNumber,
        Instant convertedAt,
        List<QuotationItemDto> items,
        Long version,
        List<String> allowedActions) {

    public record QuotationItemDto(
            Long id,
            int lineNo,
            Long productId,
            String productCode,
            String productName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPct,
            BigDecimal netUnitPrice,
            BigDecimal lineTotal) {}
}
