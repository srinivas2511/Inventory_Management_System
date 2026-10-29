package com.mfg.ims.production.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.NotFoundException;

@Service
public class CapacityBoardService {

    private final NamedParameterJdbcTemplate jdbc;

    public CapacityBoardService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record MachineLoadRow(
        Long machineId,
        String machineCode,
        String machineName,
        LocalDate capacityDate,
        double availableHours,
        double scheduledHours,
        double loadPct
    ) {}

    public record ScheduledOpDto(
        Long id,
        Long productionOperationId,
        Long machineId,
        String machineCode,
        String operationName,
        String productionOrderNumber,
        String scheduledStart,
        String scheduledEnd,
        double plannedHours,
        String status
    ) {}

    public record CapacityBoardDto(
        List<MachineLoadRow> machineLoad,
        List<ScheduledOpDto> scheduledOps
    ) {}

    public record ScheduleRequest(
        Long productionOperationId,
        Long machineId,
        LocalDate targetDate,
        double plannedHours
    ) {}

    public CapacityBoardDto getCapacityBoard(LocalDate from, LocalDate to) {
        var params = new MapSqlParameterSource("from", from).addValue("to", to);

        String loadSql = """
            SELECT m.id AS machine_id, m.machine_code, m.name AS machine_name,
                   wcc.capacity_date, wcc.available_hours,
                   COALESCE(SUM(so.planned_hours), 0) AS scheduled_hours
            FROM ims.work_centre_capacity wcc
            JOIN ims.machines m ON m.id = wcc.machine_id
            LEFT JOIN ims.scheduled_operations so
                ON so.machine_id = wcc.machine_id
               AND so.scheduled_start::date = wcc.capacity_date
               AND so.status NOT IN ('CANCELLED')
            WHERE wcc.capacity_date >= :from AND wcc.capacity_date <= :to
            GROUP BY m.id, m.machine_code, m.name, wcc.capacity_date, wcc.available_hours
            ORDER BY wcc.capacity_date, m.machine_code
            """;
        List<MachineLoadRow> machineLoad;
        try {
            machineLoad = jdbc.query(loadSql, params, (rs, rn) -> {
                double avail = rs.getDouble("available_hours");
                double sched = rs.getDouble("scheduled_hours");
                double load  = avail > 0 ? Math.min(100.0, (sched / avail) * 100.0) : 0.0;
                return new MachineLoadRow(
                    rs.getLong("machine_id"),
                    rs.getString("machine_code"),
                    rs.getString("machine_name"),
                    rs.getObject("capacity_date", LocalDate.class),
                    avail, sched,
                    Math.round(load * 10.0) / 10.0
                );
            });
        } catch (Exception e) {
            machineLoad = new ArrayList<>();
        }

        String opsSql = """
            SELECT so.id, so.production_operation_id, so.machine_id,
                   m.machine_code,
                   ops.name AS operation_name,
                   pod.order_number,
                   so.scheduled_start::text AS scheduled_start,
                   so.scheduled_end::text   AS scheduled_end,
                   so.planned_hours, so.status
            FROM ims.scheduled_operations so
            LEFT JOIN ims.machines m ON m.id = so.machine_id
            LEFT JOIN ims.production_operations po_op ON po_op.id = so.production_operation_id
            LEFT JOIN ims.operations ops ON ops.id = po_op.operation_id
            LEFT JOIN ims.production_orders pod ON pod.id = po_op.production_order_id
            WHERE so.scheduled_start::date >= :from
              AND so.scheduled_start::date <= :to
              AND so.status != 'CANCELLED'
            ORDER BY so.scheduled_start
            """;
        List<ScheduledOpDto> scheduledOps;
        try {
            scheduledOps = jdbc.query(opsSql, params, (rs, rn) -> new ScheduledOpDto(
                rs.getLong("id"),
                rs.getLong("production_operation_id"),
                rs.getLong("machine_id"),
                rs.getString("machine_code"),
                rs.getString("operation_name"),
                rs.getString("order_number"),
                rs.getString("scheduled_start"),
                rs.getString("scheduled_end"),
                rs.getDouble("planned_hours"),
                rs.getString("status")
            ));
        } catch (Exception e) {
            scheduledOps = new ArrayList<>();
        }

        return new CapacityBoardDto(machineLoad, scheduledOps);
    }

    @Transactional
    public ScheduledOpDto scheduleOperation(ScheduleRequest req, Long userId) {
        String checkSql = "SELECT COUNT(*) FROM ims.production_operations WHERE id = :id";
        Long count = jdbc.queryForObject(checkSql,
                new MapSqlParameterSource("id", req.productionOperationId()), Long.class);
        if (count == null || count == 0)
            throw new NotFoundException("ProductionOperation", req.productionOperationId().toString());

        double hours = req.plannedHours() > 0 ? req.plannedHours() : 8.0;
        var start = req.targetDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        var end   = start.plusSeconds((long)(hours * 3600));

        String insertSql = """
            INSERT INTO ims.scheduled_operations
                (production_operation_id, machine_id, scheduled_start, scheduled_end,
                 planned_hours, status, created_by)
            VALUES (:opId, :machineId, :start, :end, :hours, 'SCHEDULED', :uid)
            RETURNING id, production_operation_id, machine_id,
                      scheduled_start::text, scheduled_end::text, planned_hours, status
            """;
        var params = new MapSqlParameterSource()
            .addValue("opId",      req.productionOperationId())
            .addValue("machineId", req.machineId())
            .addValue("start",     start)
            .addValue("end",       end)
            .addValue("hours",     hours)
            .addValue("uid",       userId);

        return jdbc.queryForObject(insertSql, params, (rs, rn) -> new ScheduledOpDto(
            rs.getLong("id"),
            rs.getLong("production_operation_id"),
            rs.getLong("machine_id"),
            null, null, null,
            rs.getString("scheduled_start"),
            rs.getString("scheduled_end"),
            rs.getDouble("planned_hours"),
            rs.getString("status")
        ));
    }
}
