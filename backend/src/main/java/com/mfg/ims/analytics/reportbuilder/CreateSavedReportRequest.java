package com.mfg.ims.analytics.reportbuilder;

import java.util.Map;

public record CreateSavedReportRequest(
        String name,
        String sourceView,
        Map<String, Object> definition,
        String shareScope
) {}
