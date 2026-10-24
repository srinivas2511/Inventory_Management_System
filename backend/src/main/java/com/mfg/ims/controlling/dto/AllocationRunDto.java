package com.mfg.ims.controlling.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AllocationRunDto(
        Long id,
        Long periodId,
        String periodName,
        String status,
        Instant runAt,
        Long runBy,
        Long journalHeaderId,
        BigDecimal totalAllocated,
        List<AllocationLineDto> lines
) {}
