package com.mfg.ims.finance.ap.dto;

import com.mfg.ims.finance.tax.dto.TaxDtos.TaxLineDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        Long journalHeaderId,
        String matchStatus,
        BigDecimal matchExpectedAmount,
        BigDecimal matchVarianceAmount,
        String matchMessage,
        String taxSlabCode,
        String supplyType,
        List<TaxLineDto> taxLines,
        String currencyCode,
        BigDecimal exchangeRate,
        BigDecimal totalInr,
        BigDecimal priceVarianceInr,
        BigDecimal debitedAmount
) {}
