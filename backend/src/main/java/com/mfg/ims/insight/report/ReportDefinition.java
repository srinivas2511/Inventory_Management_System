package com.mfg.ims.insight.report;

import java.util.List;
import java.util.Map;

public interface ReportDefinition {
    String reportCode();
    String displayName();
    List<String> requiredAuthority();
    List<String> columnHeaders();
    List<Map<String, Object>> runQuery(ReportRequest req);
}
