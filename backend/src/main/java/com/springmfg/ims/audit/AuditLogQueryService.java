package com.springmfg.ims.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.common.api.PageRequests;
import com.springmfg.ims.common.api.PageResponse;
import com.springmfg.ims.common.exception.ValidationFailedException;

/** Read side of the audit log. Plain SQL so a date range prunes the monthly partitions. */
@Service
public class AuditLogQueryService {

    /** Filters; every field is optional and they combine with AND. */
    public record Filter(Long userId, String username, String entity, String entityId, String action, Instant from,
            Instant to) {
    }

    private static final Set<String> SORTABLE = Set.of("occurredAt");
    private static final String COLUMNS = """
            id, occurred_at, user_id, username, roles, action, entity, entity_id, old_value, new_value, reason,
            ip_address, correlation_id""";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    AuditLogQueryService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogDtos.AuditLogResponse> search(Filter filter, Pageable pageable) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new ValidationFailedException("from", "'from' must not be after 'to'.");
        }
        Pageable page = PageRequests.restrictSort(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "occurredAt"));
        boolean ascending = page.getSort().getOrderFor("occurredAt").isAscending();

        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> where = new ArrayList<>();
        if (filter.userId() != null) {
            where.add("user_id = :userId");
            params.addValue("userId", filter.userId());
        }
        if (notBlank(filter.username())) {
            where.add("lower(username) = lower(:username)");
            params.addValue("username", filter.username().trim());
        }
        if (notBlank(filter.entity())) {
            where.add("entity = :entity");
            params.addValue("entity", filter.entity().trim());
        }
        if (notBlank(filter.entityId())) {
            where.add("entity_id = :entityId");
            params.addValue("entityId", filter.entityId().trim());
        }
        if (notBlank(filter.action())) {
            where.add("action = :action");
            params.addValue("action", filter.action().trim());
        }
        if (filter.from() != null) {
            where.add("occurred_at >= :from");
            params.addValue("from", Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            where.add("occurred_at <= :to");
            params.addValue("to", Timestamp.from(filter.to()));
        }
        String condition = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);

        Long total = jdbc.queryForObject("SELECT count(*) FROM audit_logs" + condition, params, Long.class);
        params.addValue("limit", page.getPageSize());
        params.addValue("offset", page.getOffset());
        String order = ascending ? "occurred_at ASC, id ASC" : "occurred_at DESC, id DESC";
        List<AuditLogDtos.AuditLogResponse> rows = jdbc.query("SELECT " + COLUMNS + " FROM audit_logs" + condition
                + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", params, (rs, n) -> new AuditLogDtos.AuditLogResponse(
                        rs.getLong("id"), rs.getTimestamp("occurred_at").toInstant(), (Long) rs.getObject("user_id"),
                        rs.getString("username"), rs.getString("roles"), rs.getString("action"), rs.getString("entity"),
                        rs.getString("entity_id"), parse(rs.getString("old_value")), parse(rs.getString("new_value")),
                        rs.getString("reason"), rs.getString("ip_address"), rs.getString("correlation_id")));
        long totalElements = total == null ? 0 : total;
        int totalPages = (int) Math.ceil(totalElements / (double) page.getPageSize());
        return new PageResponse<>(rows, page.getPageNumber(), page.getPageSize(), totalElements, totalPages);
    }

    private JsonNode parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return json.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored audit value is not valid JSON", e);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
