package com.mfg.ims.finance.ap.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One payment against a supplier invoice, or one receipt against a customer invoice (FIN-009). {@code carryingInr} is
 * what the settlement took off the payable or receivable and {@code fxDifference} is the realised exchange gain (+) or
 * loss (-) booked with it; both are zero for an invoice in rupees.
 */
public record SettlementDto(
        Long id,
        String reference,
        LocalDate date,
        BigDecimal amount,
        String paymentMode,
        String referenceNumber,
        BigDecimal exchangeRate,
        BigDecimal carryingInr,
        BigDecimal fxDifference,
        Long journalHeaderId) {}
