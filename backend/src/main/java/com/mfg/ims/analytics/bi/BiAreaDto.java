package com.mfg.ims.analytics.bi;

import java.math.BigDecimal;
import java.util.Map;

public record BiAreaDto(
        String area,
        long totalCount,
        BigDecimal totalAmount,
        Map<String, Object> summary
) {}
