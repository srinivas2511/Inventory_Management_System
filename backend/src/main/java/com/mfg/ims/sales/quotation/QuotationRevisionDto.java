package com.mfg.ims.sales.quotation;

import java.time.Instant;

public record QuotationRevisionDto(
        Long id,
        Long quotationId,
        int revisionNumber,
        Instant revisedAt,
        Long revisedBy,
        String reason,
        String snapshot
) {}
