package com.mfg.ims.quality.dto;

import java.math.BigDecimal;

public record ParetoEntryDto(
        Long reasonId,
        String reasonCode,
        String reasonName,
        BigDecimal totalQty,
        long count,
        double pct
) {}
