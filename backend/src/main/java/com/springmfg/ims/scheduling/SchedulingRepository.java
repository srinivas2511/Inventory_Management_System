package com.springmfg.ims.scheduling;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only SQL behind the scheduler. People data (employees, skills, attendance, leave) is read with SQL rather than
 * through the HR module's classes, which stay unaware of planning; production data is written through the production
 * repositories by {@link SchedulingService}.
 */
@Repository
public class SchedulingRepository {

    /** A routing operation together with what the scheduler needs to know about its order. */
    public record OpRow(
            long opId,
            long orderId,
            String orderNumber,
            int priority,
            LocalDate dueDate,
            BigDecimal plannedQty,
            String orderStatus,
            String productCode,
            int sequenceNo,
            long operationMasterId,
            String operationName,
            String defaultMachineType,
            BigDecimal standardTimeMin,
            BigDecimal setupTimeMin,
            Long machineId,
            Long operatorId,
            String operatorName,
            String status,
            Instant scheduledStart,
            Instant scheduledEnd,
            Instant startedAt,
            Instant completedAt) {

        /** Setup once plus standard time for every piece of the order, in whole minutes, rounded up. */
        public long durationMinutes() {
            BigDecimal minutes = setupTimeMin.add(standardTimeMin.multiply(plannedQty));
            return minutes.setScale(0, java.math.RoundingMode.CEILING).longValueExact();
        }
    }

    public record MaintenanceBlock(long machineId, long requestId, String requestNumber, LocalDate date,
                                   String requestType) {
    }

    public record OperatorRow(long employeeId, long userId) {
    }

    public record SkillRow(long employeeId, Long machineId, Long operationId, int proficiency) {
    }

    private static final String OPERATION_SELECT = """
            SELECT op.id, op.production_order_id, po.order_number, po.priority, po.expected_completion_date,
                   po.planned_qty, po.status AS order_status, p.product_code, op.sequence_no, op.operation_id,
                   o.name AS operation_name, o.default_machine_type, o.standard_time_min, o.setup_time_min,
                   op.machine_id, op.assigned_operator_id, u.full_name AS operator_name, op.status,
                   op.scheduled_start, op.scheduled_end, op.started_at, op.completed_at
              FROM ims.production_operations op
              JOIN ims.production_orders po ON po.id = op.production_order_id
              JOIN ims.products p ON p.id = po.product_id
              JOIN ims.operations o ON o.id = op.operation_id
              LEFT JOIN ims.users u ON u.id = op.assigned_operator_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SchedulingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every operation of the released or running orders, in routing order. {@code orderIds} empty means all orders. */
    public List<OpRow> operations(List<Long> orderIds) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("allOrders", orderIds == null || orderIds.isEmpty())
                .addValue("orderIds", orderIds == null || orderIds.isEmpty() ? List.of(-1L) : orderIds);
        return jdbc.query(OPERATION_SELECT + """
                 WHERE po.status IN ('RELEASED', 'IN_PROGRESS')
                   AND (:allOrders = TRUE OR po.id IN (:orderIds))
                 ORDER BY po.id, op.sequence_no
                """, p, (rs, i) -> toOp(rs));
    }

    public Optional<OpRow> operation(long opId) {
        return jdbc.query(OPERATION_SELECT + " WHERE op.id = :id", new MapSqlParameterSource("id", opId),
                (rs, i) -> toOp(rs)).stream().findFirst();
    }

    /** All operations of one order, for the sequence rule. */
    public List<OpRow> operationsOfOrder(long orderId) {
        return jdbc.query(OPERATION_SELECT + " WHERE po.id = :id ORDER BY op.sequence_no",
                new MapSqlParameterSource("id", orderId), (rs, i) -> toOp(rs));
    }

    /** Pending or running operations that hold a window on a machine, other than {@code exceptOpId}. */
    public List<OpRow> scheduledOnMachine(long machineId, long exceptOpId) {
        return jdbc.query(OPERATION_SELECT + """
                 WHERE op.machine_id = :machineId AND op.id <> :exceptId
                   AND op.status IN ('PENDING', 'IN_PROGRESS') AND op.scheduled_start IS NOT NULL
                 ORDER BY op.scheduled_start
                """, new MapSqlParameterSource().addValue("machineId", machineId).addValue("exceptId", exceptOpId),
                (rs, i) -> toOp(rs));
    }

    /** Operations whose window meets [fromTs, toTs), for the Gantt chart; machineId 0 means every machine. */
    public List<OpRow> inWindow(Instant fromTs, Instant toTs, long machineId) {
        return jdbc.query(OPERATION_SELECT + """
                 WHERE op.scheduled_start IS NOT NULL AND op.status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')
                   AND op.scheduled_start < :toTs AND op.scheduled_end > :fromTs
                   AND (:machineId = 0 OR op.machine_id = :machineId)
                 ORDER BY op.machine_id, op.scheduled_start
                """, new MapSqlParameterSource().addValue("fromTs", Timestamp.from(fromTs))
                        .addValue("toTs", Timestamp.from(toTs)).addValue("machineId", machineId),
                (rs, i) -> toOp(rs));
    }

    /** Pending operations of released or running orders that have no window yet. */
    public List<OpRow> unscheduled() {
        return jdbc.query(OPERATION_SELECT + """
                 WHERE po.status IN ('RELEASED', 'IN_PROGRESS') AND op.status = 'PENDING'
                   AND op.scheduled_start IS NULL
                 ORDER BY po.priority, po.id, op.sequence_no
                """, new MapSqlParameterSource(), (rs, i) -> toOp(rs));
    }

    /** When each order's last scheduled operation ends, for the late flag. */
    public Map<Long, Instant> scheduledEndByOrder() {
        Map<Long, Instant> out = new HashMap<>();
        jdbc.query("""
                SELECT production_order_id, MAX(scheduled_end) AS ends
                  FROM ims.production_operations
                 WHERE scheduled_end IS NOT NULL AND status IN ('PENDING', 'IN_PROGRESS')
                 GROUP BY production_order_id
                """, new MapSqlParameterSource(), (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                out.put(rs.getLong("production_order_id"), rs.getTimestamp("ends").toInstant()));
        return out;
    }

    /** Machine days taken by open maintenance requests that name a date. */
    public List<MaintenanceBlock> maintenance(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT id, machine_id, request_number, scheduled_date, request_type
                  FROM ims.maintenance_requests
                 WHERE status IN ('OPEN', 'IN_PROGRESS') AND scheduled_date BETWEEN :from AND :to
                 ORDER BY scheduled_date, id
                """, new MapSqlParameterSource().addValue("from", java.sql.Date.valueOf(from))
                        .addValue("to", java.sql.Date.valueOf(to)),
                (rs, i) -> new MaintenanceBlock(rs.getLong("machine_id"), rs.getLong("id"),
                        rs.getString("request_number"), rs.getDate("scheduled_date").toLocalDate(),
                        rs.getString("request_type")));
    }

    /** Active employees with a login who are employed at some point in the range. */
    public List<OperatorRow> operators(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT e.id, e.user_id FROM ims.employees e
                 WHERE e.active AND e.user_id IS NOT NULL AND e.date_of_joining <= :to
                   AND (e.date_of_leaving IS NULL OR e.date_of_leaving >= :from)
                 ORDER BY e.id
                """, new MapSqlParameterSource().addValue("from", java.sql.Date.valueOf(from))
                        .addValue("to", java.sql.Date.valueOf(to)),
                (rs, i) -> new OperatorRow(rs.getLong("id"), rs.getLong("user_id")));
    }

    /** Machine and operation skills that are active and not expired on {@code from}. */
    public List<SkillRow> skills(LocalDate from) {
        return jdbc.query("""
                SELECT employee_id, machine_id, operation_id, COALESCE(proficiency_level, 1) AS proficiency
                  FROM ims.employee_skill_matrix
                 WHERE active AND (machine_id IS NOT NULL OR operation_id IS NOT NULL)
                   AND (expires_on IS NULL OR expires_on >= :from)
                """, new MapSqlParameterSource("from", java.sql.Date.valueOf(from)),
                (rs, i) -> new SkillRow(rs.getLong("employee_id"), (Long) rs.getObject("machine_id"),
                        (Long) rs.getObject("operation_id"), rs.getInt("proficiency")));
    }

    /** Days an employee will not be at work: marked absent, on leave or a holiday, or on approved leave. */
    public Map<Long, Set<LocalDate>> unavailableDays(LocalDate from, LocalDate to) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("from", java.sql.Date.valueOf(from))
                .addValue("to", java.sql.Date.valueOf(to));
        Map<Long, Set<LocalDate>> out = new HashMap<>();
        jdbc.query("""
                SELECT employee_id, attendance_date FROM ims.attendance
                 WHERE attendance_date BETWEEN :from AND :to AND status IN ('ABSENT', 'LEAVE', 'HOLIDAY')
                """, p, (org.springframework.jdbc.core.RowCallbackHandler) rs -> out
                .computeIfAbsent(rs.getLong("employee_id"), k -> new HashSet<>())
                .add(rs.getDate("attendance_date").toLocalDate()));
        jdbc.query("""
                SELECT employee_id, from_date, to_date FROM ims.leave_applications
                 WHERE status = 'APPROVED' AND from_date <= :to AND to_date >= :from
                """, p, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            Set<LocalDate> days = out.computeIfAbsent(rs.getLong("employee_id"), k -> new HashSet<>());
            LocalDate start = rs.getDate("from_date").toLocalDate();
            LocalDate end = rs.getDate("to_date").toLocalDate();
            for (LocalDate d = start.isBefore(from) ? from : start; !d.isAfter(end.isAfter(to) ? to : end);
                 d = d.plusDays(1)) {
                days.add(d);
            }
        });
        return out;
    }

    private static OpRow toOp(ResultSet rs) throws SQLException {
        return new OpRow(rs.getLong("id"), rs.getLong("production_order_id"), rs.getString("order_number"),
                rs.getInt("priority"), date(rs, "expected_completion_date"), rs.getBigDecimal("planned_qty"),
                rs.getString("order_status"), rs.getString("product_code"), rs.getInt("sequence_no"),
                rs.getLong("operation_id"), rs.getString("operation_name"), rs.getString("default_machine_type"),
                rs.getBigDecimal("standard_time_min"), rs.getBigDecimal("setup_time_min"),
                (Long) rs.getObject("machine_id"), (Long) rs.getObject("assigned_operator_id"),
                rs.getString("operator_name"), rs.getString("status"), instant(rs, "scheduled_start"),
                instant(rs, "scheduled_end"), instant(rs, "started_at"), instant(rs, "completed_at"));
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        java.sql.Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    /** Skills grouped by employee, as the engine wants them. */
    public static Map<Long, List<SkillRow>> byEmployee(List<SkillRow> skills) {
        Map<Long, List<SkillRow>> out = new HashMap<>();
        for (SkillRow s : skills) {
            out.computeIfAbsent(s.employeeId(), k -> new ArrayList<>()).add(s);
        }
        return out;
    }
}
