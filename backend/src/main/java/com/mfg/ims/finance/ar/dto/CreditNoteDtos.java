package com.mfg.ims.finance.ar.dto;

import com.mfg.ims.finance.tax.dto.TaxDtos.TaxLineDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CreditNoteDtos {

    private CreditNoteDtos() {}

    /**
     * Credits {@code subtotal} (in the invoice currency, before tax) against an invoice; the tax is reversed in the same
     * proportion. A return names a batch that was dispatched on the invoice and the place the goods are received.
     */
    public record CreateCreditNoteRequest(
            @NotNull Long arInvoiceId,
            @NotNull LocalDate creditDate,
            @NotNull @Positive BigDecimal subtotal,
            @NotBlank @Size(max = 300) String reason,
            Long returnFgBatchId,
            @Positive BigDecimal returnQuantity,
            Long returnWarehouseId,
            Long returnLocationId) {}

    public record CreditNoteDto(
            Long id,
            String creditNoteNumber,
            Long arInvoiceId,
            String invoiceNumber,
            String currencyCode,
            LocalDate creditDate,
            String reason,
            BigDecimal subtotal,
            BigDecimal taxTotal,
            BigDecimal totalAmount,
            String status,
            Long journalHeaderId,
            Long createdBy,
            Long approvedBy,
            Instant approvedAt,
            Long returnFgBatchId,
            BigDecimal returnQuantity,
            String returnTransactionNumber,
            List<TaxLineDto> taxLines) {}
}
