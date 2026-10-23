package com.mfg.ims.finance.ap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ApDebitNoteDtos {

    private ApDebitNoteDtos() {}

    /** Credits the given subtotal (in invoice currency, before tax) against a supplier invoice. */
    public record CreateDebitNoteRequest(
            @NotNull Long apInvoiceId,
            @NotNull LocalDate debitDate,
            @NotNull @Positive BigDecimal subtotal,
            @NotBlank @Size(max = 300) String reason) {}

    public record ApDebitNoteDto(
            Long id,
            String debitNoteNumber,
            Long apInvoiceId,
            String invoiceNumber,
            String currencyCode,
            LocalDate debitDate,
            String reason,
            BigDecimal subtotal,
            BigDecimal taxTotal,
            BigDecimal totalAmount,
            String status,
            Long journalHeaderId,
            Long createdBy,
            Long approvedBy,
            Instant approvedAt) {}
}
