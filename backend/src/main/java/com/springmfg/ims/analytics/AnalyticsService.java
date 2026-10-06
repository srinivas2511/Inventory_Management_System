package com.springmfg.ims.analytics;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.analytics.AnalyticsDtos.QueryDescriptor;
import com.springmfg.ims.analytics.AnalyticsDtos.QueryResult;
import com.springmfg.ims.common.exception.NotFoundException;

/**
 * Runs the catalogue queries. Every parameter is bound with a concrete value (the defaults are applied here, never
 * left null), so PostgreSQL never has to guess an untyped null. A query that exposes margins or financial results
 * needs {@code ANALYTICS_FINANCE} on top of the endpoint's {@code ANALYTICS_VIEW}.
 */
@Service
public class AnalyticsService {

    public static final int DEFAULT_LIMIT = 1000;
    public static final int MAX_LIMIT = 10_000;
    static final int DEFAULT_MONTHS = 12;

    private final NamedParameterJdbcTemplate jdbc;
    private final AnalyticsCatalogue catalogue;

    public AnalyticsService(NamedParameterJdbcTemplate jdbc, AnalyticsCatalogue catalogue) {
        this.jdbc = jdbc;
        this.catalogue = catalogue;
    }

    public List<QueryDescriptor> catalogue() {
        List<QueryDescriptor> visible = new ArrayList<>();
        for (AnalyticsQuery q : catalogue.all()) {
            if (isPermitted(q)) {
                visible.add(new QueryDescriptor(q.key(), q.title(), q.description(), q.category(), q.chart(),
                        q.xColumn(), q.yColumns(), q.monthly(), q.columns()));
            }
        }
        return visible;
    }

    @Transactional(readOnly = true)
    public QueryResult run(String key, LocalDate from, LocalDate to, Integer limit) {
        AnalyticsQuery q = catalogue.find(key);
        if (q == null) {
            throw new NotFoundException("Analytics query", key);
        }
        if (!isPermitted(q)) {
            throw new AccessDeniedException("Not permitted to run " + q.key());
        }
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusMonths(DEFAULT_MONTHS).withDayOfMonth(1);
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        LocalDate effectiveFrom = q.monthly() ? start.withDayOfMonth(1) : start;
        int max = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);

        var params = new MapSqlParameterSource()
                .addValue("from", Date.valueOf(effectiveFrom))
                .addValue("to", Date.valueOf(end))
                .addValue("limit", max + 1);
        List<Map<String, Object>> raw = jdbc.queryForList(q.sql(), params);
        boolean truncated = raw.size() > max;
        List<Map<String, Object>> rows = new ArrayList<>(Math.min(raw.size(), max));
        for (Map<String, Object> r : raw.subList(0, Math.min(raw.size(), max))) {
            rows.add(normalise(r));
        }
        return new QueryResult(q.key(), q.title(), effectiveFrom, end, q.columns(), rows, rows.size(), truncated,
                Instant.now());
    }

    private static Map<String, Object> normalise(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> {
            if (v instanceof Date d) {
                out.put(k, d.toLocalDate().toString());
            } else if (v instanceof Timestamp t) {
                out.put(k, t.toInstant().toString());
            } else {
                out.put(k, v);
            }
        });
        return out;
    }

    private static boolean isPermitted(AnalyticsQuery q) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (q.requiredAuthority().equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
