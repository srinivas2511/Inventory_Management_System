package com.mfg.ims.finance.tds;

import java.math.BigDecimal;

/** Aggregate TDS totals grouped by TDS section (e.g. 194C, 194J). */
public record TdsSummaryDto(
        String tdsSection,
        String description,
        long entryCount,
        BigDecimal totalBaseAmount,
        BigDecimal totalTdsAmount
) {}
