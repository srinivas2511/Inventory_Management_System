package com.mfg.ims.finance.gl.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JournalHeaderDto(
        Long id,
        String journalNumber,
        String journalType,
        String sourceModule,
        String sourceDocumentType,
        Long sourceDocumentId,
        LocalDate postingDate,
        Long periodId,
        String description,
        String currencyCode,
        BigDecimal exchangeRate,
        String status,
        Long reversalOfId,
        Long preparedBy,
        Long approvedBy,
        Instant approvedAt,
        Long postedBy,
        Instant postedAt,
        List<JournalLineDto> lines
) {}
