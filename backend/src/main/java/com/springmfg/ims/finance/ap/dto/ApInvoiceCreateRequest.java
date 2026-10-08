package com.springmfg.ims.finance.ap.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ApInvoiceCreateRequest(
        @NotNull Long supplierId,
        String supplierInvoiceNo,
        @NotNull LocalDate invoiceDate,
        @NotNull LocalDate dueDate,
        @NotNull Long goodsReceiptId,
        @NotNull @Positive BigDecimal subtotal,
        BigDecimal taxTotal,
        @Size(max = 15) String taxSlabCode
) {
    /** Tax given as an amount (no slab). */
    public ApInvoiceCreateRequest(Long supplierId, String supplierInvoiceNo, LocalDate invoiceDate, LocalDate dueDate,
                                  Long goodsReceiptId, BigDecimal subtotal, BigDecimal taxTotal) {
        this(supplierId, supplierInvoiceNo, invoiceDate, dueDate, goodsReceiptId, subtotal, taxTotal, null);
    }
}
