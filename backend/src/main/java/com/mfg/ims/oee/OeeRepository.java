package com.mfg.ims.oee;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads the per-machine, per-day figures OEE is built from. {@link #liveDaily} runs the query behind
 * {@code mv_oee_daily} (migration V33) directly on the tables, restricted to the range asked for;
 * {@link #viewDaily} reads the materialized view. The two must stay textually parallel: a test compares them.
 */
@Repository
public class OeeRepository {

    /** A machine's recorded output and downtime on one business day. */
    public record DailyRow(
            long machineId,
            LocalDate day,
            BigDecimal goodQty,
            BigDecimal badQty,
            BigDecimal idealMinutes,
            BigDecimal badIdealMinutes,
            BigDecimal plannedDowntimeMinutes,
            BigDecimal unplannedDowntimeMinutes) {
    }

    /** Downtime minutes within a range for one category and reason. */
    public record DowntimeRow(String category, String reason, long occurrences, BigDecimal minutes) {
    }

    private static final String LIVE_DAILY = """
            WITH output AS (
                SELECT COALESCE(po.machine_id, op.machine_id) AS machine_id,
                       (COALESCE(po.end_time, po.created_at) AT TIME ZONE ims.business_tz())::date AS day,
                       SUM(po.good_qty)::numeric(18,3) AS good_qty,
                       SUM(po.rejected_qty + po.scrap_qty)::numeric(18,3) AS bad_qty,
                       SUM((po.good_qty + po.rejected_qty + po.scrap_qty) * o.standard_time_min)::numeric(18,3) AS ideal_minutes,
                       SUM((po.rejected_qty + po.scrap_qty) * o.standard_time_min)::numeric(18,3) AS bad_ideal_minutes
                  FROM ims.production_outputs po
                  JOIN ims.production_operations op ON op.id = po.production_operation_id
                  JOIN ims.operations o ON o.id = op.operation_id
                 WHERE COALESCE(po.machine_id, op.machine_id) IS NOT NULL
                   AND (:machineId = 0 OR COALESCE(po.machine_id, op.machine_id) = :machineId)
                   AND COALESCE(po.end_time, po.created_at) >= :fromTs AND COALESCE(po.end_time, po.created_at) < :toTs
                 GROUP BY 1, 2
            ), downtime AS (
                SELECT d.machine_id, g.day::date AS day,
                       SUM(CASE WHEN d.category = 'PLANNED_MAINTENANCE' THEN
                           EXTRACT(EPOCH FROM (LEAST(COALESCE(d.ended_at, now()), ((g.day::date + 1)::timestamp AT TIME ZONE ims.business_tz()))
                                             - GREATEST(d.started_at, (g.day::date::timestamp AT TIME ZONE ims.business_tz())))) / 60
                           ELSE 0 END)::numeric(18,3) AS planned_min,
                       SUM(CASE WHEN d.category IS DISTINCT FROM 'PLANNED_MAINTENANCE' THEN
                           EXTRACT(EPOCH FROM (LEAST(COALESCE(d.ended_at, now()), ((g.day::date + 1)::timestamp AT TIME ZONE ims.business_tz()))
                                             - GREATEST(d.started_at, (g.day::date::timestamp AT TIME ZONE ims.business_tz())))) / 60
                           ELSE 0 END)::numeric(18,3) AS unplanned_min
                  FROM ims.production_downtime d
                 CROSS JOIN LATERAL generate_series((d.started_at AT TIME ZONE ims.business_tz())::date::timestamp,
                                                    (COALESCE(d.ended_at, now()) AT TIME ZONE ims.business_tz())::date::timestamp,
                                                    interval '1 day') AS g(day)
                 WHERE COALESCE(d.ended_at, now()) > d.started_at
                   AND d.started_at < :toTs AND COALESCE(d.ended_at, now()) > :fromTs
                   AND (:machineId = 0 OR d.machine_id = :machineId)
                 GROUP BY d.machine_id, g.day::date
            ), pm AS (
                SELECT r.machine_id, (a.performed_at AT TIME ZONE ims.business_tz())::date AS day,
                       SUM(a.duration_minutes)::numeric(18,3) AS planned_min
                  FROM ims.maintenance_activities a
                  JOIN ims.maintenance_requests r ON r.id = a.maintenance_request_id
                 WHERE r.request_type = 'PREVENTIVE' AND COALESCE(a.duration_minutes, 0) > 0
                   AND (:machineId = 0 OR r.machine_id = :machineId)
                   AND a.performed_at >= :fromTs AND a.performed_at < :toTs
                 GROUP BY 1, 2
            )
            SELECT machine_id, day,
                   SUM(good_qty)::numeric(18,3) AS good_qty, SUM(bad_qty)::numeric(18,3) AS bad_qty,
                   SUM(ideal_minutes)::numeric(18,3) AS ideal_minutes,
                   SUM(bad_ideal_minutes)::numeric(18,3) AS bad_ideal_minutes,
                   SUM(planned_min)::numeric(18,3) AS planned_downtime_min,
                   SUM(unplanned_min)::numeric(18,3) AS unplanned_downtime_min
              FROM (
                    SELECT machine_id, day, good_qty, bad_qty, ideal_minutes, bad_ideal_minutes,
                           0::numeric AS planned_min, 0::numeric AS unplanned_min FROM output
                    UNION ALL
                    SELECT machine_id, day, 0, 0, 0, 0, planned_min, unplanned_min FROM downtime
                    UNION ALL
                    SELECT machine_id, day, 0, 0, 0, 0, planned_min, 0 FROM pm
                   ) x
             WHERE day BETWEEN :from AND :to
             GROUP BY machine_id, day
            """;

    private static final String VIEW_DAILY = """
            SELECT machine_id, day, good_qty, bad_qty, ideal_minutes, bad_ideal_minutes,
                   planned_downtime_min, unplanned_downtime_min
              FROM ims.mv_oee_daily
             WHERE (:machineId = 0 OR machine_id = :machineId) AND day BETWEEN :from AND :to
            """;

    private static final String DOWNTIME_BREAKDOWN = """
            SELECT COALESCE(d.category, 'OTHER') AS category, d.reason, COUNT(*) AS occurrences,
                   SUM(EXTRACT(EPOCH FROM (LEAST(COALESCE(d.ended_at, now()), :toTs) - GREATEST(d.started_at, :fromTs))) / 60)::numeric(18,3) AS minutes
              FROM ims.production_downtime d
             WHERE d.started_at < :toTs AND COALESCE(d.ended_at, now()) > :fromTs
               AND COALESCE(d.ended_at, now()) > d.started_at
               AND (:machineId = 0 OR d.machine_id = :machineId)
             GROUP BY COALESCE(d.category, 'OTHER'), d.reason
             ORDER BY minutes DESC, d.reason
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public OeeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All machines when {@code machineId} is 0. {@code from}/{@code to} are inclusive business days. */
    public List<DailyRow> liveDaily(long machineId, LocalDate from, LocalDate to, Instant fromTs, Instant toTs) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("machineId", machineId)
                .addValue("from", java.sql.Date.valueOf(from))
                .addValue("to", java.sql.Date.valueOf(to))
                .addValue("fromTs", Timestamp.from(fromTs))
                .addValue("toTs", Timestamp.from(toTs));
        return jdbc.query(LIVE_DAILY, p, (rs, i) -> toRow(rs));
    }

    public List<DailyRow> viewDaily(long machineId, LocalDate from, LocalDate to) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("machineId", machineId)
                .addValue("from", java.sql.Date.valueOf(from))
                .addValue("to", java.sql.Date.valueOf(to));
        return jdbc.query(VIEW_DAILY, p, (rs, i) -> toRow(rs));
    }

    public List<DowntimeRow> downtimeBreakdown(long machineId, Instant fromTs, Instant toTs) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("machineId", machineId)
                .addValue("fromTs", Timestamp.from(fromTs))
                .addValue("toTs", Timestamp.from(toTs));
        return jdbc.query(DOWNTIME_BREAKDOWN, p, (rs, i) -> new DowntimeRow(rs.getString("category"),
                rs.getString("reason"), rs.getLong("occurrences"), rs.getBigDecimal("minutes")));
    }

    public void refreshView() {
        jdbc.getJdbcOperations().execute("REFRESH MATERIALIZED VIEW CONCURRENTLY ims.mv_oee_daily");
    }

    private static DailyRow toRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DailyRow(rs.getLong("machine_id"), rs.getDate("day").toLocalDate(),
                nz(rs.getBigDecimal("good_qty")), nz(rs.getBigDecimal("bad_qty")),
                nz(rs.getBigDecimal("ideal_minutes")), nz(rs.getBigDecimal("bad_ideal_minutes")),
                nz(rs.getBigDecimal("planned_downtime_min")), nz(rs.getBigDecimal("unplanned_downtime_min")));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
