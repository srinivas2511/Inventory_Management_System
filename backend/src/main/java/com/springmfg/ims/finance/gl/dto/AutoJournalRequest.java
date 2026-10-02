package com.springmfg.ims.finance.gl.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AutoJournalRequest(
        String journalType,
        String sourceModule,
        String sourceDocumentType,
        Long sourceDocumentId,
        LocalDate postingDate,
        String description,
        Long preparedBy,
        List<JournalLineRequest> lines
) {}
