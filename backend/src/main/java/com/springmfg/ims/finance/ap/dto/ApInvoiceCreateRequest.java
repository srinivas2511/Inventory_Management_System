package com.springmfg.ims.finance.ap.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ApInvoiceCreateRequest(
        @NotNull Long supplierId,
        String supplierInvoiceNo,
        @NotNull LocalDate invoiceDate,
        @NotNull LocalDate dueDate,
        Long goodsReceiptId,
        @NotNull @Positive BigDecimal subtotal,
        BigDecimal taxTotal
) {}
