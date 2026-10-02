package com.springmfg.ims.insight.report;

import java.util.List;
import java.util.Map;

public record ReportResult(
        String reportCode,
        String displayName,
        List<String> headers,
        List<Map<String, Object>> rows,
        int totalRows) {}
