package com.springmfg.ims.finance.ar.dto;

import com.springmfg.ims.finance.tax.dto.TaxDtos.TaxLineDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        Long journalHeaderId,
        String supplyType,
        List<TaxLineDto> taxLines,
        String currencyCode,
        BigDecimal exchangeRate,
        BigDecimal totalInr
) {}
