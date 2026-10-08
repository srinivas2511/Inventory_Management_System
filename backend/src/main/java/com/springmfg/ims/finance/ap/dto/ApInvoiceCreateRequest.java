package com.springmfg.ims.finance.ap.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A supplier invoice. Amounts are in {@code currencyCode} (the supplier's currency when omitted); the goods receipt it
 * bills is valued in INR, so the match compares the invoice at the exchange rate of its date.
 */
public record ApInvoiceCreateRequest(
        @NotNull Long supplierId,
        String supplierInvoiceNo,
        @NotNull LocalDate invoiceDate,
        @NotNull LocalDate dueDate,
        @NotNull Long goodsReceiptId,
        @NotNull @Positive BigDecimal subtotal,
        BigDecimal taxTotal,
        @Size(max = 15) String taxSlabCode,
        @Pattern(regexp = "^([A-Z]{3})?$", message = "Use a three-letter currency code such as USD") String currencyCode
) {
    /** Tax given as an amount (no slab), in the supplier's currency. */
    public ApInvoiceCreateRequest(Long supplierId, String supplierInvoiceNo, LocalDate invoiceDate, LocalDate dueDate,
                                  Long goodsReceiptId, BigDecimal subtotal, BigDecimal taxTotal) {
        this(supplierId, supplierInvoiceNo, invoiceDate, dueDate, goodsReceiptId, subtotal, taxTotal, null, null);
    }

    /** Tax determined from a slab, in the supplier's currency. */
    public ApInvoiceCreateRequest(Long supplierId, String supplierInvoiceNo, LocalDate invoiceDate, LocalDate dueDate,
                                  Long goodsReceiptId, BigDecimal subtotal, BigDecimal taxTotal, String taxSlabCode) {
        this(supplierId, supplierInvoiceNo, invoiceDate, dueDate, goodsReceiptId, subtotal, taxTotal, taxSlabCode, null);
    }
}
