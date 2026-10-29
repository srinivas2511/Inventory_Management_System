package com.mfg.ims.analytics.reportbuilder;

import java.time.Instant;
import java.util.Map;

public record SavedReportDto(
        Long id,
        String name,
        String sourceView,
        Map<String, Object> definition,
        String shareScope,
        Long ownerId,
        Instant createdAt
) {}
