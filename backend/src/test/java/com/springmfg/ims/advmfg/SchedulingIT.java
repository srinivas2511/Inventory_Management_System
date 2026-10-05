package com.springmfg.ims.advmfg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.prodcalendar.ProductionCalendar;
import com.springmfg.ims.scheduling.SchedulingDtos.GanttDto;
import com.springmfg.ims.scheduling.SchedulingDtos.PlacementDto;
import com.springmfg.ims.scheduling.SchedulingDtos.RescheduleRequest;
import com.springmfg.ims.scheduling.SchedulingDtos.RescheduleResultDto;
import com.springmfg.ims.scheduling.SchedulingDtos.RunScheduleRequest;
import com.springmfg.ims.scheduling.SchedulingDtos.SchedulePlanDto;
import com.springmfg.ims.scheduling.SchedulingService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * Production scheduling against a real database (R18-3.x). Every test builds machines, an operation master and orders
 * of its own with a unique machine type and restricts each run to its own orders, so leftovers from other tests only
 * ever appear as machine load, never as something to assert on.
 */
class SchedulingIT extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(1000);

    @Autowired SchedulingService scheduling;
    @Autowired ProductionCalendar calendar;
    @Autowired TestData td;
    @Autowired JdbcTemplate jdbc;

    private String tag;
    private String type;
    private long userId;
    private long operationMasterId;

    @BeforeEach
    void setup() {
        tag = "SCH" + SEQ.incrementAndGet();
        type = "TYPE-" + tag;
        userId = newUser("admin");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, tag, true, 1, Set.of(), Set.of()), null, List.of()));
        // standard 1.00 min per piece, 20 min setup: an order of 100 pieces runs 120 minutes
        operationMasterId = newOperation("1.00", "20");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────────────────────

    private long newUser(String suffix) {
        String name = tag + "-" + suffix + "-" + SEQ.incrementAndGet();
        return jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Scheduling IT', ?, 'x') RETURNING id""", Long.class, name, name + "@it.test");
    }

    private long newMachine(String suffix, String machineType) {
        return jdbc.queryForObject("""
                INSERT INTO ims.machines (machine_code, name, machine_type) VALUES (?, 'Sched machine', ?) RETURNING id""",
                Long.class, "MC-" + tag + suffix, machineType);
    }

    private long newOperation(String standard, String setup) {
        return jdbc.queryForObject("""
                INSERT INTO ims.operations (operation_code, name, default_machine_type, standard_time_min, setup_time_min)
                VALUES (?, 'Sched operation', ?, ?, ?) RETURNING id""", Long.class, "OP-" + tag + "-" + SEQ.incrementAndGet(),
                type, new BigDecimal(standard), new BigDecimal(setup));
    }

    private long newOrder(int priority, int qty, LocalDate due) {
        var product = td.product();
        var bom = td.draftBom(product.getId());
        return jdbc.queryForObject("""
                INSERT INTO ims.production_orders (order_number, product_id, bom_id, planned_qty, priority, status,
                       expected_completion_date)
                VALUES (?, ?, ?, ?, ?, 'RELEASED', ?) RETURNING id""", Long.class, "PO-" + tag + "-" + SEQ.incrementAndGet(),
                product.getId(), bom.getId(), qty, priority, due == null ? null : java.sql.Date.valueOf(due));
    }

    private long newOp(long orderId, int seq, Long machineId) {
        return jdbc.queryForObject("""
                INSERT INTO ims.production_operations (production_order_id, sequence_no, operation_id, machine_id, status)
                VALUES (?, ?, ?, ?, 'PENDING') RETURNING id""", Long.class, orderId, seq, operationMasterId, machineId);
    }

    private long newEmployee(long userId, Long skilledMachine) {
        long employee = jdbc.queryForObject("""
                INSERT INTO ims.employees (employee_code, user_id, first_name, last_name, date_of_joining, employment_type)
                VALUES (?, ?, 'Sched', 'Operator', DATE '2020-01-01', 'PERMANENT') RETURNING id""", Long.class,
                "E" + tag + SEQ.incrementAndGet(), userId);
        if (skilledMachine != null) {
            jdbc.update("""
                    INSERT INTO ims.employee_skill_matrix (employee_id, skill_type, machine_id, proficiency_level)
                    VALUES (?, 'MACHINE', ?, 3)""", employee, skilledMachine);
        }
        return employee;
    }

    private SchedulePlanDto dryRun(List<Long> orders) {
        return scheduling.run(new RunScheduleRequest(14, orders, null), false);
    }

    private SchedulePlanDto apply(List<Long> orders, String mode) {
        return scheduling.run(new RunScheduleRequest(14, orders, mode), true);
    }

    private static PlacementDto placed(SchedulePlanDto plan, long opId) {
        return plan.placements().stream().filter(p -> p.productionOperationId() == opId).findFirst().orElseThrow();
    }

    private Map<String, Object> opRow(long opId) {
        return jdbc.queryForMap("""
                SELECT machine_id, assigned_operator_id, scheduled_start, scheduled_end, status
                  FROM ims.production_operations WHERE id = ?""", opId);
    }

    private Instant startOf(long opId) {
        return ((Timestamp) opRow(opId).get("scheduled_start")).toInstant();
    }

    private Instant endOf(long opId) {
        return ((Timestamp) opRow(opId).get("scheduled_end")).toInstant();
    }

    private LocalDate dayOf(Instant instant) {
        return instant.atZone(calendar.zone()).toLocalDate();
    }

    // ── tests ───────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void a_run_plans_in_priority_order_picks_the_machine_that_finishes_first_and_the_skilled_operator() {
        long m1 = newMachine("A", type);
        long m2 = newMachine("B", type);
        long urgent = newOrder(1, 600, null);        // 20 + 600 x 1.0 = 620 min
        long routine = newOrder(2, 600, null);
        long opUrgent = newOp(urgent, 1, null);
        long opRoutine = newOp(routine, 1, null);
        long u1 = newUser("op1");
        long u2 = newUser("op2");
        newEmployee(u1, m1);
        newEmployee(u2, m2);

        SchedulePlanDto plan = dryRun(List.of(urgent, routine));

        assertThat(plan.applied()).isFalse();
        assertThat(plan.placements()).hasSize(2);
        PlacementDto first = placed(plan, opUrgent);
        PlacementDto second = placed(plan, opRoutine);
        assertThat(first.machineId()).as("both machines are free; the lower code wins the tie").isEqualTo(m1);
        assertThat(first.operatorUserId()).isEqualTo(u1);
        assertThat(second.machineId()).as("m1 is taken by the urgent order, so m2 finishes first").isEqualTo(m2);
        assertThat(second.operatorUserId()).isEqualTo(u2);
        assertThat(first.start()).isAfterOrEqualTo(plan.horizonStart());
        assertThat(Duration.between(first.start(), first.end()).toMinutes()).isGreaterThanOrEqualTo(620);
        assertThat(opRow(opUrgent).get("scheduled_start")).as("a dry run saves nothing").isNull();

        SchedulePlanDto applied = apply(List.of(urgent, routine), null);

        assertThat(applied.applied()).isTrue();
        assertThat(opRow(opUrgent).get("machine_id")).isEqualTo(m1);
        assertThat(opRow(opUrgent).get("assigned_operator_id")).isEqualTo(u1);
        assertThat(opRow(opRoutine).get("machine_id")).isEqualTo(m2);
        assertThat(opRow(opRoutine).get("assigned_operator_id")).isEqualTo(u2);
        // the applied run plans afresh (a moment later), so compare with what it reported, not with the dry run
        PlacementDto appliedUrgent = placed(applied, opUrgent);
        assertThat(startOf(opUrgent)).isEqualTo(appliedUrgent.start());
        assertThat(endOf(opUrgent)).isEqualTo(appliedUrgent.end());
        assertThat(appliedUrgent.machineId()).isEqualTo(first.machineId());
        assertThat(appliedUrgent.operatorUserId()).isEqualTo(first.operatorUserId());
    }

    @Test
    void an_operator_who_is_absent_or_on_approved_leave_when_the_job_runs_is_not_assigned() {
        long m1 = newMachine("A", type);
        long order = newOrder(1, 100, null);
        long op = newOp(order, 1, null);
        long u1 = newUser("op1");
        long employee = newEmployee(u1, m1);

        PlacementDto baseline = placed(dryRun(List.of(order)), op);
        assertThat(baseline.operatorUserId()).isEqualTo(u1);

        // absent on the day it would run
        jdbc.update("INSERT INTO ims.attendance (employee_id, attendance_date, status) VALUES (?, ?, 'ABSENT')",
                employee, java.sql.Date.valueOf(dayOf(baseline.start())));
        PlacementDto whenAbsent = placed(dryRun(List.of(order)), op);
        assertThat(whenAbsent.operatorUserId()).isNull();
        assertThat(whenAbsent.warnings()).containsExactly("NO_AVAILABLE_OPERATOR");

        // present again, but on approved leave over that day
        jdbc.update("DELETE FROM ims.attendance WHERE employee_id = ?", employee);
        long leaveType = jdbc.queryForObject("SELECT id FROM ims.leave_types WHERE leave_code = 'CL'", Long.class);
        jdbc.update("""
                INSERT INTO ims.leave_applications (application_number, employee_id, leave_type_id, from_date, to_date,
                       days, status)
                VALUES (?, ?, ?, ?, ?, 3, 'APPROVED')""", "LV-" + tag + SEQ.incrementAndGet(), employee, leaveType,
                java.sql.Date.valueOf(dayOf(baseline.start()).minusDays(1)),
                java.sql.Date.valueOf(dayOf(baseline.start()).plusDays(1)));
        assertThat(placed(dryRun(List.of(order)), op).operatorUserId()).isNull();
    }

    @Test
    void with_nobody_in_the_skill_matrix_for_the_machine_the_job_is_still_placed_with_a_warning() {
        newMachine("A", type);
        long order = newOrder(1, 100, null);
        long op = newOp(order, 1, null);

        PlacementDto p = placed(dryRun(List.of(order)), op);

        assertThat(p.operatorUserId()).isNull();
        assertThat(p.warnings()).containsExactly("NO_SKILLED_OPERATOR");
    }

    @Test
    void an_operation_never_starts_before_the_one_before_it_in_its_routing_ends() {
        long m1 = newMachine("A", type);
        long m2 = newMachine("B", type);
        long order = newOrder(1, 100, null);
        long first = newOp(order, 1, m1);
        long second = newOp(order, 2, m2);

        SchedulePlanDto plan = dryRun(List.of(order));

        assertThat(placed(plan, second).start()).isAfterOrEqualTo(placed(plan, first).end());
    }

    @Test
    void a_maintenance_day_on_the_machine_blocks_it_for_the_whole_day() {
        long m1 = newMachine("A", type);
        long order = newOrder(1, 100, null);
        long op = newOp(order, 1, m1);

        PlacementDto before = placed(dryRun(List.of(order)), op);
        LocalDate blocked = dayOf(before.start());
        jdbc.update("""
                INSERT INTO ims.maintenance_requests (request_number, machine_id, request_type, description, requested_by,
                       scheduled_date) VALUES (?, ?, 'PREVENTIVE', 'IT PM', ?, ?)""", "MR/IT/" + SEQ.incrementAndGet(), m1,
                userId, java.sql.Date.valueOf(blocked));

        PlacementDto after = placed(dryRun(List.of(order)), op);

        assertThat(dayOf(after.start())).isAfter(blocked);
        GanttDto gantt = scheduling.gantt(blocked, blocked.plusDays(7), m1);
        assertThat(gantt.machines()).singleElement().satisfies(m -> assertThat(m.maintenance())
                .extracting(b -> b.date()).contains(blocked));
    }

    @Test
    void an_order_that_cannot_finish_by_its_due_date_is_flagged_late_and_a_missing_machine_type_is_unplaced() {
        newMachine("A", type);
        long late = newOrder(1, 100, LocalDate.now(calendar.zone()).minusDays(1));
        newOp(late, 1, null);
        long noMachine = newOrder(2, 100, null);
        long strandedMaster = jdbc.queryForObject("""
                INSERT INTO ims.operations (operation_code, name, default_machine_type, standard_time_min, setup_time_min)
                VALUES (?, 'Stranded operation', ?, 1.00, 0) RETURNING id""", Long.class,
                "OPS-" + tag + "-" + SEQ.incrementAndGet(), "NO-SUCH-TYPE-" + tag);
        long stranded = jdbc.queryForObject("""
                INSERT INTO ims.production_operations (production_order_id, sequence_no, operation_id, status)
                VALUES (?, 1, ?, 'PENDING') RETURNING id""", Long.class, noMachine, strandedMaster);

        SchedulePlanDto plan = dryRun(List.of(late, noMachine));

        assertThat(plan.orders()).filteredOn(o -> o.productionOrderId() == late).singleElement()
                .satisfies(o -> assertThat(o.late()).isTrue());
        assertThat(plan.unplaced()).extracting(u -> u.productionOperationId() + ":" + u.reason())
                .contains(stranded + ":NO_MACHINE");
    }

    @Test
    void only_unscheduled_mode_keeps_existing_windows_and_full_mode_replans_by_priority() {
        long machine = newMachine("A", type);
        long routine = newOrder(3, 100, null);
        long opRoutine = newOp(routine, 1, machine);
        apply(List.of(routine), null);
        Instant routineStart = startOf(opRoutine);

        long urgent = newOrder(1, 100, null);
        long opUrgent = newOp(urgent, 1, machine);
        apply(List.of(routine, urgent), SchedulingService.MODE_ONLY_UNSCHEDULED);

        assertThat(startOf(opRoutine)).as("an existing window is kept").isEqualTo(routineStart);
        assertThat(startOf(opUrgent)).as("the urgent order waits behind the held window")
                .isAfterOrEqualTo(endOf(opRoutine));

        apply(List.of(routine, urgent), SchedulingService.MODE_FULL);

        assertThat(startOf(opUrgent)).as("a full replan puts the urgent order first").isBefore(startOf(opRoutine));
    }

    @Test
    void work_already_running_on_a_machine_blocks_it_for_new_orders() {
        long machine = newMachine("A", type);
        long running = newOrder(2, 100, null);
        long opRunning = newOp(running, 1, machine);
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE ims.production_operations SET status = 'IN_PROGRESS', started_at = ?, scheduled_start = ?,
                       scheduled_end = ? WHERE id = ?""", Timestamp.from(now.minusSeconds(600)),
                Timestamp.from(now.minusSeconds(600)), Timestamp.from(now.plus(Duration.ofHours(3))), opRunning);
        long next = newOrder(1, 30, null);
        long opNext = newOp(next, 1, machine);

        PlacementDto p = placed(dryRun(List.of(next)), opNext);

        // the database keeps microseconds; allow for the rounding of the nanoseconds of the test's clock
        assertThat(p.start()).isAfterOrEqualTo(now.plus(Duration.ofHours(3)).minusMillis(1));
    }

    @Test
    void a_planner_can_move_an_operation_but_not_onto_another_job_before_its_predecessor_or_into_the_past() {
        long machine = newMachine("A", type);
        long otherMachine = newMachine("B", "OTHER-" + type);
        long c1 = newOrder(1, 100, null);
        long c2 = newOrder(2, 100, null);
        long opC1 = newOp(c1, 1, machine);
        long opC2 = newOp(c2, 1, machine);
        apply(List.of(c1, c2), null);
        Instant s1 = startOf(opC1);
        Instant e1 = endOf(opC1);
        assertThat(startOf(opC2)).isAfterOrEqualTo(e1);

        // onto the first job: conflict
        assertThatThrownBy(() -> scheduling.reschedule(opC2, new RescheduleRequest(machine, s1.plusSeconds(1800))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.SCHEDULE_CONFLICT));

        // into the past
        assertThatThrownBy(() -> scheduling.reschedule(opC2,
                new RescheduleRequest(machine, Instant.now().minus(Duration.ofDays(1)))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("past");

        // to a free time three days later: accepted, never earlier than asked, and persisted
        Instant target = e1.plus(Duration.ofDays(3));
        RescheduleResultDto moved = scheduling.reschedule(opC2, new RescheduleRequest(machine, target));
        assertThat(moved.bar().start()).isAfterOrEqualTo(target);
        assertThat(startOf(opC2)).isEqualTo(moved.bar().start());
        assertThat(endOf(opC2)).isEqualTo(moved.bar().end());

        // to a machine of another type: allowed, with a warning
        RescheduleResultDto elsewhere = scheduling.reschedule(opC2,
                new RescheduleRequest(otherMachine, e1.plus(Duration.ofDays(4))));
        assertThat(elsewhere.warnings()).contains("MACHINE_TYPE_DIFFERS_FROM_OPERATION");
        assertThat(opRow(opC2).get("machine_id")).isEqualTo(otherMachine);

        // before the operation preceding it in the routing ends
        long e = newOrder(3, 100, null);
        long first = newOp(e, 1, machine);
        long second = newOp(e, 2, machine);
        apply(List.of(e), SchedulingService.MODE_ONLY_UNSCHEDULED);
        Instant firstEnd = endOf(first);
        assertThatThrownBy(() -> scheduling.reschedule(second,
                new RescheduleRequest(machine, firstEnd.minusSeconds(900))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("preceding");

        // a started operation cannot be moved or unscheduled
        jdbc.update("UPDATE ims.production_operations SET status = 'IN_PROGRESS' WHERE id = ?", opC1);
        assertThatThrownBy(() -> scheduling.reschedule(opC1, new RescheduleRequest(machine, target.plus(Duration.ofDays(10)))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(x -> assertThat(((BusinessRuleException) x).getCode()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
        assertThatThrownBy(() -> scheduling.unschedule(opC1)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void the_gantt_lists_bars_per_machine_working_days_and_what_is_still_unscheduled() {
        long machine = newMachine("A", type);
        long scheduled = newOrder(1, 100, null);
        long opScheduled = newOp(scheduled, 1, machine);
        apply(List.of(scheduled), null);
        long waiting = newOrder(2, 100, null);
        long opWaiting = newOp(waiting, 1, machine);
        LocalDate today = LocalDate.now(calendar.zone());

        GanttDto gantt = scheduling.gantt(today, today.plusDays(13), null);

        assertThat(gantt.machines()).filteredOn(m -> m.machineId() == machine).singleElement().satisfies(m -> {
            assertThat(m.bars()).extracting(b -> b.productionOperationId()).contains(opScheduled).doesNotContain(opWaiting);
            assertThat(m.bars()).filteredOn(b -> b.productionOperationId() == opScheduled).singleElement()
                    .satisfies(b -> {
                        assertThat(b.orderNumber()).startsWith("PO-" + tag);
                        assertThat(b.locked()).isFalse();
                    });
        });
        assertThat(gantt.workingDays()).isNotEmpty().allMatch(calendar::isWorkingDay);
        assertThat(gantt.unscheduled()).extracting(u -> u.productionOperationId()).contains(opWaiting);

        scheduling.unschedule(opScheduled);
        assertThat(opRow(opScheduled).get("scheduled_start")).isNull();
        assertThat(scheduling.gantt(today, today.plusDays(13), null).unscheduled())
                .extracting(u -> u.productionOperationId()).contains(opScheduled);
    }

    @Test
    void a_schedule_view_is_limited_to_a_couple_of_months_and_must_run_forwards() {
        LocalDate today = LocalDate.now(calendar.zone());
        assertThatThrownBy(() -> scheduling.gantt(today, today.plusDays(120), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("at most");
        assertThatThrownBy(() -> scheduling.gantt(today, today.minusDays(1), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void an_unknown_mode_is_a_validation_error() {
        assertThatThrownBy(() -> scheduling.run(new RunScheduleRequest(7, List.of(), "EVERYTHING"), false))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("EVERYTHING");
    }
}
