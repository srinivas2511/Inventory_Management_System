package com.springmfg.ims.finance.ar.dto;

import java.time.LocalDate;

/** A dispatch that left the warehouse but has no AR invoice yet. */
public record MissingArInvoiceDto(Long dispatchId, String dispatchNumber, Long customerId, LocalDate dispatchDate) {
}
