package com.mfg.ims.analytics;

import java.util.List;

/**
 * One pre-built analytics query. The SQL is fixed code with three bound parameters (:from, :to, :limit), never
 * assembled from user input. {@code monthly} queries read month-bucketed views, so :from is moved to the first day
 * of its month.
 */
public record AnalyticsQuery(
        String key,
        String title,
        String description,
        String category,
        String requiredAuthority,
        boolean monthly,
        String chart,
        String xColumn,
        List<String> yColumns,
        List<Column> columns,
        String sql) {

    public record Column(String name, String label, String type) {
    }

    public static Column text(String name, String label) {
        return new Column(name, label, "TEXT");
    }

    public static Column date(String name, String label) {
        return new Column(name, label, "DATE");
    }

    public static Column number(String name, String label) {
        return new Column(name, label, "NUMBER");
    }

    public static Column money(String name, String label) {
        return new Column(name, label, "MONEY");
    }

    public static Column percent(String name, String label) {
        return new Column(name, label, "PERCENT");
    }
}
