package com.springmfg.ims.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record QueryDescriptor(String key, String title, String description, String category, String chart,
                                  String xColumn, List<String> yColumns, boolean monthly,
                                  List<AnalyticsQuery.Column> columns) {
    }

    public record QueryResult(String key, String title, LocalDate from, LocalDate to,
                              List<AnalyticsQuery.Column> columns, List<Map<String, Object>> rows, int rowCount,
                              boolean truncated, Instant generatedAt) {
    }

    public record RefreshResult(List<String> refreshed, Map<String, String> failed) {
    }
}
