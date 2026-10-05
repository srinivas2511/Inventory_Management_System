package com.springmfg.ims.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.springmfg.ims.scheduling.engine.ScheduleEngine;
import com.springmfg.ims.scheduling.engine.ScheduleEngine.Interval;
import com.springmfg.ims.scheduling.engine.ScheduleEngine.MachineSlot;
import com.springmfg.ims.scheduling.engine.ScheduleEngine.OpRequest;
import com.springmfg.ims.scheduling.engine.ScheduleEngine.OperatorSlot;
import com.springmfg.ims.scheduling.engine.ScheduleEngine.Placement;
import com.springmfg.ims.scheduling.engine.ScheduleEngine.Plan;
import com.springmfg.ims.scheduling.engine.ScheduleEngine.UnplacedReason;
import com.springmfg.ims.scheduling.engine.ScheduleEngine.Warning;

/**
 * The scheduler on a small calendar worked out by hand: Monday 5 October 2026 and Tuesday 6 October, each with one
 * working window 09:00-17:00 (480 minutes), UTC. Everything else is a weekly off.
 */
class ScheduleEngineTest {

    private static final LocalDate MON_DATE = LocalDate.of(2026, 10, 5);
    private static final Instant MON_00 = MON_DATE.atStartOfDay().toInstant(ZoneOffset.UTC);

    private static Instant mon(int hour, int minute) {
        return MON_00.plusSeconds(hour * 3600L + minute * 60L);
    }

    private static Instant tue(int hour, int minute) {
        return mon(hour, minute).plusSeconds(24 * 3600L);
    }

    private static final List<Interval> WINDOWS = List.of(
            new Interval(mon(9, 0), mon(17, 0)),
            new Interval(tue(9, 0), tue(17, 0)));

    private static final MachineSlot M1 = new MachineSlot(1, "M1", "COIL");
    private static final MachineSlot M2 = new MachineSlot(2, "M2", "COIL");

    private static OpRequest op(long id, long orderId, int priority, LocalDate due, int seq, Long machine,
                                String type, long minutes) {
        return new OpRequest(id, orderId, "PO-" + orderId, priority, due, seq, 100 + seq, type, machine, minutes, null);
    }

    private static Plan run(List<OpRequest> ops, List<MachineSlot> machines, Map<Long, List<Interval>> busy,
                            List<OperatorSlot> operators) {
        return ScheduleEngine.schedule(ops, machines, WINDOWS, busy, operators, Map.of(), mon(0, 0), ZoneOffset.UTC);
    }

    private static Placement placement(Plan plan, long opId) {
        return plan.placements().stream().filter(p -> p.operationId() == opId).findFirst().orElseThrow();
    }

    @Test
    void the_more_urgent_order_is_placed_first_and_the_next_one_follows_on_the_same_machine() {
        // Order 2 is priority 1 (300 min): Mon 09:00-14:00. Order 1 (priority 2, 300 min) starts 14:00:
        // 180 min left on Monday, the remaining 120 on Tuesday 09:00-11:00.
        Plan plan = run(List.of(op(11, 1, 2, null, 1, 1L, null, 300), op(21, 2, 1, null, 1, 1L, null, 300)),
                List.of(M1), Map.of(), List.of());

        Placement urgent = placement(plan, 21);
        assertThat(urgent.span()).isEqualTo(new Interval(mon(9, 0), mon(14, 0)));

        Placement second = placement(plan, 11);
        assertThat(second.span()).isEqualTo(new Interval(mon(14, 0), tue(11, 0)));
        assertThat(second.segments()).containsExactly(new Interval(mon(14, 0), mon(17, 0)),
                new Interval(tue(9, 0), tue(11, 0)));
    }

    @Test
    void equal_priority_is_decided_by_the_earlier_due_date_then_orders_without_a_due_date() {
        Plan plan = run(List.of(
                        op(11, 1, 3, null, 1, 1L, null, 60),
                        op(21, 2, 3, LocalDate.of(2026, 10, 9), 1, 1L, null, 60),
                        op(31, 3, 3, LocalDate.of(2026, 10, 7), 1, 1L, null, 60)),
                List.of(M1), Map.of(), List.of());

        assertThat(placement(plan, 31).span().start()).isEqualTo(mon(9, 0));
        assertThat(placement(plan, 21).span().start()).isEqualTo(mon(10, 0));
        assertThat(placement(plan, 11).span().start()).isEqualTo(mon(11, 0));
    }

    @Test
    void work_never_runs_across_another_job_so_spans_on_a_machine_do_not_interleave() {
        // M1 is taken Mon 12:00-13:00. A 300 min job could start 09:00 and would have to straddle that block, so it
        // restarts after it: 13:00-17:00 (240) and Tuesday 09:00-10:00 (60).
        Plan plan = run(List.of(op(11, 1, 1, null, 1, 1L, null, 300)), List.of(M1),
                Map.of(1L, List.of(new Interval(mon(12, 0), mon(13, 0)))), List.of());

        assertThat(placement(plan, 11).span()).isEqualTo(new Interval(mon(13, 0), tue(10, 0)));
    }

    @Test
    void a_job_that_fits_before_a_busy_block_is_placed_there() {
        Plan plan = run(List.of(op(11, 1, 1, null, 1, 1L, null, 120)), List.of(M1),
                Map.of(1L, List.of(new Interval(mon(12, 0), mon(13, 0)))), List.of());

        assertThat(placement(plan, 11).span()).isEqualTo(new Interval(mon(9, 0), mon(11, 0)));
    }

    @Test
    void the_machine_that_lets_the_job_finish_earliest_is_chosen_and_ties_go_to_the_lower_code() {
        // M1 is busy until 15:00; M2 is free.
        Plan busyFirst = run(List.of(op(11, 1, 1, null, 1, null, "COIL", 120)), List.of(M1, M2),
                Map.of(1L, List.of(new Interval(mon(9, 0), mon(15, 0)))), List.of());
        assertThat(placement(busyFirst, 11).machineId()).isEqualTo(2);
        assertThat(placement(busyFirst, 11).span()).isEqualTo(new Interval(mon(9, 0), mon(11, 0)));

        Plan tie = run(List.of(op(11, 1, 1, null, 1, null, "COIL", 120)), List.of(M2, M1), Map.of(), List.of());
        assertThat(placement(tie, 11).machineId()).isEqualTo(1);
    }

    @Test
    void only_machines_of_the_operations_type_are_candidates() {
        MachineSlot grinder = new MachineSlot(3, "G1", "GRIND");
        Plan plan = run(List.of(op(11, 1, 1, null, 1, null, "GRIND", 60)), List.of(M1, grinder), Map.of(), List.of());

        assertThat(placement(plan, 11).machineId()).isEqualTo(3);
    }

    @Test
    void an_operation_starts_only_after_the_one_before_it_in_its_routing_ends() {
        // Seq 1 on M1 for 120 min (09:00-11:00). Seq 2 is on M2, free all day, but must wait until 11:00.
        Plan plan = run(List.of(op(11, 1, 1, null, 1, 1L, null, 120), op(12, 1, 1, null, 2, 2L, null, 60)),
                List.of(M1, M2), Map.of(), List.of());

        assertThat(placement(plan, 11).span().end()).isEqualTo(mon(11, 0));
        assertThat(placement(plan, 12).span()).isEqualTo(new Interval(mon(11, 0), mon(12, 0)));
    }

    @Test
    void a_predecessor_outside_the_run_holds_the_operation_back_until_it_is_done() {
        OpRequest waiting = new OpRequest(12, 1, "PO-1", 1, null, 2, 102, null, 1L, 60, mon(13, 0));
        Plan plan = run(List.of(waiting), List.of(M1), Map.of(), List.of());

        assertThat(placement(plan, 12).span().start()).isEqualTo(mon(13, 0));
    }

    @Test
    void work_that_does_not_fit_in_the_horizon_is_unplaced_and_blocks_the_rest_of_its_routing() {
        // 2000 min is more than the 960 working minutes in the horizon.
        Plan plan = run(List.of(op(11, 1, 1, null, 1, 1L, null, 2000), op(12, 1, 1, null, 2, 1L, null, 30)),
                List.of(M1), Map.of(), List.of());

        assertThat(plan.placements()).isEmpty();
        assertThat(plan.unplaced()).extracting(u -> u.operationId() + ":" + u.reason()).containsExactly(
                "11:" + UnplacedReason.HORIZON_EXCEEDED, "12:" + UnplacedReason.PREDECESSOR_UNPLACED);
    }

    @Test
    void an_operation_with_no_machine_of_its_type_is_unplaced() {
        Plan plan = run(List.of(op(11, 1, 1, null, 1, null, "WELD", 30)), List.of(M1), Map.of(), List.of());

        assertThat(plan.unplaced()).singleElement().satisfies(u -> assertThat(u.reason())
                .isEqualTo(UnplacedReason.NO_MACHINE));
    }

    @Test
    void an_order_ending_after_its_due_date_is_flagged_late() {
        Plan late = run(List.of(op(11, 1, 1, MON_DATE, 1, 1L, null, 600)), List.of(M1), Map.of(), List.of());
        assertThat(late.orders()).singleElement().satisfies(o -> {
            assertThat(o.endsAt()).isEqualTo(tue(11, 0));   // 480 on Monday + 120 on Tuesday
            assertThat(o.late()).isTrue();
        });

        Plan onTime = run(List.of(op(11, 1, 1, MON_DATE.plusDays(1), 1, 1L, null, 600)), List.of(M1), Map.of(),
                List.of());
        assertThat(onTime.orders()).singleElement().satisfies(o -> assertThat(o.late()).isFalse());
    }

    private static OperatorSlot operator(long userId, Map<Long, Integer> machineSkill, Set<LocalDate> away) {
        return new OperatorSlot(userId, machineSkill, Map.of(), away);
    }

    @Test
    void the_most_proficient_available_operator_is_assigned() {
        Plan plan = run(List.of(op(11, 1, 1, null, 1, 1L, null, 60)), List.of(M1), Map.of(), List.of(
                operator(7, Map.of(1L, 2), Set.of()),
                operator(8, Map.of(1L, 4), Set.of()),
                operator(9, Map.of(2L, 5), Set.of())));   // skilled on a different machine only

        assertThat(placement(plan, 11).operatorUserId()).isEqualTo(8L);
        assertThat(placement(plan, 11).warnings()).isEmpty();
    }

    @Test
    void an_operator_who_is_away_on_a_day_the_job_touches_is_skipped() {
        Plan plan = run(List.of(op(11, 1, 1, null, 1, 1L, null, 60)), List.of(M1), Map.of(), List.of(
                operator(7, Map.of(1L, 2), Set.of()),
                operator(8, Map.of(1L, 4), Set.of(MON_DATE))));

        assertThat(placement(plan, 11).operatorUserId()).isEqualTo(7L);
    }

    @Test
    void no_skilled_operator_and_no_available_operator_are_different_warnings() {
        Plan unskilled = run(List.of(op(11, 1, 1, null, 1, 1L, null, 60)), List.of(M1), Map.of(),
                List.of(operator(9, Map.of(2L, 5), Set.of())));
        assertThat(placement(unskilled, 11).operatorUserId()).isNull();
        assertThat(placement(unskilled, 11).warnings()).containsExactly(Warning.NO_SKILLED_OPERATOR);

        Plan away = run(List.of(op(11, 1, 1, null, 1, 1L, null, 60)), List.of(M1), Map.of(),
                List.of(operator(8, Map.of(1L, 4), Set.of(MON_DATE))));
        assertThat(placement(away, 11).operatorUserId()).isNull();
        assertThat(placement(away, 11).warnings()).containsExactly(Warning.NO_AVAILABLE_OPERATOR);
    }

    @Test
    void with_equal_skill_the_operator_with_less_work_so_far_gets_the_next_job() {
        // Both operators are proficiency 3. Job 1 goes to the lower user id (7); job 2 follows on M1 and
        // operator 7 is then busier than operator 8, so 8 gets it.
        Plan plan = run(List.of(op(11, 1, 1, null, 1, 1L, null, 60), op(21, 2, 2, null, 1, 1L, null, 60)),
                List.of(M1), Map.of(), List.of(
                        operator(7, Map.of(1L, 3), Set.of()),
                        operator(8, Map.of(1L, 3), Set.of())));

        assertThat(placement(plan, 11).operatorUserId()).isEqualTo(7L);
        assertThat(placement(plan, 21).operatorUserId()).isEqualTo(8L);
    }

    @Test
    void an_operator_already_busy_at_that_time_is_not_double_booked() {
        // Two jobs on different machines at the same time; only operator 7 is skilled on both machines.
        Plan plan = run(List.of(op(11, 1, 1, null, 1, 1L, null, 60), op(21, 2, 2, null, 1, 2L, null, 60)),
                List.of(M1, M2), Map.of(), List.of(operator(7, Map.of(1L, 3, 2L, 3), Set.of())));

        assertThat(placement(plan, 11).operatorUserId()).isEqualTo(7L);
        assertThat(placement(plan, 21).operatorUserId()).isNull();
        assertThat(placement(plan, 21).warnings()).containsExactly(Warning.NO_AVAILABLE_OPERATOR);
    }

    @Test
    void place_starts_inside_a_window_when_the_earliest_time_is_mid_shift() {
        var segments = ScheduleEngine.place(WINDOWS, List.of(), mon(12, 0), 60);

        assertThat(segments.orElseThrow()).containsExactly(new Interval(mon(12, 0), mon(13, 0)));
    }
}
