package com.springmfg.ims.finance.ap.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ApInvoiceDto(
        Long id,
        String invoiceNumber,
        String supplierInvoiceNo,
        Long supplierId,
        LocalDate invoiceDate,
        LocalDate dueDate,
        Long goodsReceiptId,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal balanceAmount,
        String status,
        Long periodId,
        Long journalHeaderId
) {}
