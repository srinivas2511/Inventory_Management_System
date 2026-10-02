package com.springmfg.ims.finance.ar.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ArInvoiceDto(
        Long id,
        String invoiceNumber,
        Long customerId,
        Long dispatchId,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal totalAmount,
        BigDecimal receivedAmount,
        BigDecimal balanceAmount,
        String status,
        Long periodId,
        Long journalHeaderId
) {}
