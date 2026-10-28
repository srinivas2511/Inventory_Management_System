package com.mfg.ims.procurement.purchaseinvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseInvoiceDto(
        Long id,
        String docNo,
        String status,
        Long supplierId,
        String supplierInvoiceNo,
        LocalDate supplierInvoiceDate,
        Long poId,
        String matchStatus,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal total,
        BigDecimal paidAmount,
        LocalDate dueDate,
        List<ItemDto> items) {

    public record ItemDto(
            Long id,
            int lineNo,
            Long materialId,
            Long productId,
            String description,
            BigDecimal qty,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String matchStatus) {}
}
