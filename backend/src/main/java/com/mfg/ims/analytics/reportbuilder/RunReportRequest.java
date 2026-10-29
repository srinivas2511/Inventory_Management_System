package com.mfg.ims.analytics.reportbuilder;

import java.util.List;

public record RunReportRequest(
        String viewName,
        List<String> fields
) {}
