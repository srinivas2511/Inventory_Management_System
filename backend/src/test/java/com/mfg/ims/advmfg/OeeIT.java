package com.mfg.ims.advmfg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.oee.OeeDtos.DowntimeReasonDto;
import com.mfg.ims.oee.OeeDtos.OeeDto;
import com.mfg.ims.oee.OeeDtos.OeeLossDto;
import com.mfg.ims.oee.OeeDtos.OeeLossesDto;
import com.mfg.ims.oee.OeeDtos.OeeTrendPointDto;
import com.mfg.ims.oee.OeeRepository;
import com.mfg.ims.oee.OeeRepository.DailyRow;
import com.mfg.ims.oee.OeeService;
import com.mfg.ims.prodcalendar.ProductionCalendar;
import com.mfg.ims.production.dto.RecordDowntimeRequest;
import com.mfg.ims.production.service.ProductionDowntimeService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * OEE against a real database (R18-2.x, R18-4.1). Every test uses a machine of its own, so the figures asserted are
 * exactly the ones inserted, and a past working day, so the whole shift counts as planned time.
 *
 * <p>The day used: a 9 hour shift (540 min). 400 good pieces and 20 bad ones (15 rejected + 5 scrapped) at a standard
 * time of 1 minute each = 420 ideal minutes. A 30 min breakdown and a 15 min setup = 45 unplanned minutes.
 * Operating 495 -> availability 495/540 = 0.9167, performance 420/495 = 0.8485, quality 400/420 = 0.9524,
 * OEE = 0.7407 (= 400/540).
 */
class OeeIT extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(1000);

    @Autowired OeeService oee;
    @Autowired OeeRepository oeeRepo;
    @Autowired ProductionCalendar calendar;
    @Autowired ProductionDowntimeService downtimeService;
    @Autowired TestData td;
    @Autowired JdbcTemplate jdbc;

    private long userId;
    private long machineId;
    private long operationId;
    private long orderId;
    private long productionOperationId;
    private LocalDate day;

    @BeforeEach
    void setup() {
        String tag = "OEE" + SEQ.incrementAndGet();
        userId = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'OEE IT', ?, 'x') RETURNING id""", Long.class, tag, tag + "@it.test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, tag, true, 1, Set.of(), Set.of()), null, List.of()));
        machineId = newMachine(tag);
        operationId = jdbc.queryForObject("""
                INSERT INTO ims.operations (operation_code, name, default_machine_type, standard_time_min, setup_time_min)
                VALUES (?, 'IT coiling', ?, 1.00, 0) RETURNING id""", Long.class, "OP-" + tag, "TYPE-" + tag);
        var product = td.product();
        var bom = td.draftBom(product.getId());
        orderId = jdbc.queryForObject("""
                INSERT INTO ims.production_orders (order_number, product_id, bom_id, planned_qty, status)
                VALUES (?, ?, ?, 1000, 'IN_PROGRESS') RETURNING id""", Long.class, "PO-" + tag, product.getId(),
                bom.getId());
        productionOperationId = jdbc.queryForObject("""
                INSERT INTO ims.production_operations (production_order_id, sequence_no, operation_id, machine_id, status)
                VALUES (?, 1, ?, ?, 'IN_PROGRESS') RETURNING id""", Long.class, orderId, operationId, machineId);

        assertThat(calendar.shiftMinutesPerDay()).as("the default GENERAL shift is 09:00-18:00").isEqualTo(540);
        day = LocalDate.now(calendar.zone()).minusDays(1);
        while (!calendar.isWorkingDay(day)) {
            day = day.minusDays(1);
        }
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private long newMachine(String tag) {
        return jdbc.queryForObject("""
                INSERT INTO ims.machines (machine_code, name, machine_type) VALUES (?, 'OEE machine', ?) RETURNING id""",
                Long.class, "MC-" + tag + "-" + SEQ.incrementAndGet(), "TYPE-" + tag);
    }

    private Instant at(LocalDate d, int hour, int minute) {
        return d.atTime(hour, minute).atZone(calendar.zone()).toInstant();
    }

    private void output(long machine, String good, String rejected, String scrap, Instant endTime) {
        jdbc.update("""
                INSERT INTO ims.production_outputs (production_operation_id, production_order_id, entry_no, input_qty,
                       good_qty, rejected_qty, scrap_qty, operator_id, machine_id, start_time, end_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", productionOperationId, orderId, (int) (SEQ.incrementAndGet() % 100000),
                new BigDecimal(good).add(new BigDecimal(rejected)).add(new BigDecimal(scrap)), new BigDecimal(good),
                new BigDecimal(rejected), new BigDecimal(scrap), userId, machine, Timestamp.from(endTime.minusSeconds(3600)),
                Timestamp.from(endTime));
    }

    private void downtime(long machine, String category, String reason, Instant from, Instant to) {
        jdbc.update("""
                INSERT INTO ims.production_downtime (machine_id, reason, category, started_at, ended_at, reported_by)
                VALUES (?, ?, ?, ?, ?, ?)""", machine, reason, category, Timestamp.from(from),
                to == null ? null : Timestamp.from(to), userId);
    }

    private void worked_example_data() {
        output(machineId, "400", "15", "5", at(day, 12, 0));
        downtime(machineId, "BREAKDOWN", "Spindle bearing", at(day, 10, 0), at(day, 10, 30));
        downtime(machineId, "SETUP_CHANGEOVER", "Die change", at(day, 14, 0), at(day, 14, 15));
    }

    @Test
    void the_worked_example_from_the_design_gives_the_hand_calculated_oee() {
        worked_example_data();

        OeeDto r = oee.calculateForMachine(machineId, day, day);

        assertThat(r.availableMinutes()).isEqualByComparingTo("540");
        assertThat(r.unplannedDowntimeMinutes()).isEqualByComparingTo("45");
        assertThat(r.plannedDowntimeMinutes()).isEqualByComparingTo("0");
        assertThat(r.operatingMinutes()).isEqualByComparingTo("495");
        assertThat(r.idealMinutes()).isEqualByComparingTo("420");
        assertThat(r.goodQty()).isEqualByComparingTo("400");
        assertThat(r.badQty()).isEqualByComparingTo("20");
        assertThat(r.availability()).isEqualByComparingTo("0.9167");
        assertThat(r.performance()).isEqualByComparingTo("0.8485");
        assertThat(r.quality()).isEqualByComparingTo("0.9524");
        assertThat(r.oee()).isEqualByComparingTo("0.7407");
    }

    @Test
    void machine_downtime_reduces_availability_and_so_oee_when_it_costs_output() {
        // Day 1: a full shift, 400 good pieces. Day 2 (the working day before): a 1 hour breakdown, and the lost hour
        // costs 50 pieces (350 good). With the same output downtime would only shift loss from availability to
        // performance; OEE falls because output falls.
        LocalDate dayTwo = day.minusDays(1);
        while (!calendar.isWorkingDay(dayTwo)) {
            dayTwo = dayTwo.minusDays(1);
        }
        output(machineId, "400", "0", "0", at(day, 12, 0));
        output(machineId, "350", "0", "0", at(dayTwo, 12, 0));
        downtime(machineId, "BREAKDOWN", "Motor trip", at(dayTwo, 15, 0), at(dayTwo, 16, 0));

        OeeDto full = oee.calculateForMachine(machineId, day, day);
        OeeDto withBreakdown = oee.calculateForMachine(machineId, dayTwo, dayTwo);

        assertThat(full.availability()).isEqualByComparingTo("1.0000");
        assertThat(full.oee()).isEqualByComparingTo("0.7407");                     // 400 / 540
        assertThat(withBreakdown.unplannedDowntimeMinutes()).isEqualByComparingTo("60");
        assertThat(withBreakdown.availability()).isEqualByComparingTo("0.8889");   // 480 / 540
        assertThat(withBreakdown.oee()).isEqualByComparingTo("0.6481");            // 350 / 540
        assertThat(withBreakdown.oee()).isLessThan(full.oee());

        // the two days together: ratios of the summed minutes
        OeeDto both = oee.calculateForMachine(machineId, dayTwo, day);
        assertThat(both.availableMinutes()).isEqualByComparingTo(new BigDecimal(540L * workingDaysBetween(dayTwo, day)));
        assertThat(both.goodQty()).isEqualByComparingTo("750");
    }

    private long workingDaysBetween(LocalDate from, LocalDate to) {
        long n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (calendar.isWorkingDay(d)) {
                n++;
            }
        }
        return n;
    }

    @Test
    void the_view_and_the_live_query_give_the_same_figures_and_the_trend_matches_the_calculation() {
        worked_example_data();
        oeeRepo.refreshView();

        Instant from = at(day, 0, 0);
        Instant to = at(day.plusDays(1), 0, 0);
        List<DailyRow> live = oeeRepo.liveDaily(machineId, day, day, from, to);
        List<DailyRow> view = oeeRepo.viewDaily(machineId, day, day);

        assertThat(live).hasSize(1);
        assertThat(view).hasSize(1);
        DailyRow a = live.get(0);
        DailyRow b = view.get(0);
        assertThat(b.day()).isEqualTo(a.day());
        assertThat(b.goodQty()).isEqualByComparingTo(a.goodQty());
        assertThat(b.badQty()).isEqualByComparingTo(a.badQty());
        assertThat(b.idealMinutes()).isEqualByComparingTo(a.idealMinutes());
        assertThat(b.badIdealMinutes()).isEqualByComparingTo(a.badIdealMinutes());
        assertThat(b.plannedDowntimeMinutes()).isEqualByComparingTo(a.plannedDowntimeMinutes());
        assertThat(b.unplannedDowntimeMinutes()).isEqualByComparingTo(a.unplannedDowntimeMinutes());

        List<OeeTrendPointDto> trend = oee.trend(machineId, day, day);
        assertThat(trend).singleElement().satisfies(p -> {
            assertThat(p.day()).isEqualTo(day);
            assertThat(p.availability()).isEqualByComparingTo("0.9167");
            assertThat(p.oee()).isEqualByComparingTo("0.7407");
        });
    }

    @Test
    void the_loss_breakdown_ranks_the_big_losses_with_a_running_share() {
        worked_example_data();

        OeeLossesDto losses = oee.losses(machineId, day, day);

        // speed loss 495 - 420 = 75; breakdown 30; rejects and scrap 20 ideal min; setup 15
        assertThat(losses.totalLossMinutes()).isEqualByComparingTo("140");
        assertThat(losses.losses()).extracting(OeeLossDto::category)
                .containsExactly("SPEED_LOSS", "BREAKDOWN", "QUALITY_LOSS", "SETUP_CHANGEOVER");
        assertThat(losses.losses()).extracting(OeeLossDto::minutes).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("75"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("15"));
        assertThat(losses.losses().get(0).share()).isEqualByComparingTo("0.5357");
        assertThat(losses.losses().get(1).cumulativeShare()).isEqualByComparingTo("0.7500");
        assertThat(losses.losses().get(3).cumulativeShare()).isEqualByComparingTo("1.0000");
    }

    @Test
    void the_top_downtime_reasons_are_ranked_by_minutes_and_limited() {
        worked_example_data();
        downtime(machineId, "BREAKDOWN", "Spindle bearing", at(day, 16, 0), at(day, 16, 20));   // same reason again

        List<DowntimeReasonDto> top = oee.topDowntimeReasons(machineId, day, day, 5);

        assertThat(top).extracting(DowntimeReasonDto::reason).containsExactly("Spindle bearing", "Die change");
        assertThat(top.get(0).minutes()).isEqualByComparingTo("50");
        assertThat(top.get(0).occurrences()).isEqualTo(2);
        assertThat(oee.topDowntimeReasons(machineId, day, day, 1)).hasSize(1);
    }

    @Test
    void downtime_that_crosses_midnight_is_split_between_the_two_business_days() {
        long other = newMachine("MID" + SEQ.incrementAndGet());
        downtime(other, "BREAKDOWN", "Overnight fault", at(day, 17, 0), at(day.plusDays(1), 1, 0));

        List<DailyRow> rows = oeeRepo.liveDaily(other, day, day.plusDays(1), at(day, 0, 0), at(day.plusDays(2), 0, 0));

        assertThat(rows).extracting(DailyRow::day).containsExactly(day, day.plusDays(1));
        assertThat(rows.get(0).unplannedDowntimeMinutes()).isEqualByComparingTo("420");   // 17:00 -> 24:00
        assertThat(rows.get(1).unplannedDowntimeMinutes()).isEqualByComparingTo("60");    // 00:00 -> 01:00
    }

    @Test
    void planned_maintenance_downtime_and_preventive_work_count_as_planned_stops() {
        long other = newMachine("PM" + SEQ.incrementAndGet());
        downtime(other, "PLANNED_MAINTENANCE", "Lubrication round", at(day, 9, 0), at(day, 10, 0));

        OeeDto r = oee.calculateForMachine(other, day, day);

        assertThat(r.plannedDowntimeMinutes()).isEqualByComparingTo("60");
        assertThat(r.unplannedDowntimeMinutes()).isEqualByComparingTo("0");
        assertThat(r.availability()).isEqualByComparingTo("0.8889");   // (540 - 60) / 540, as DESIGN 24.1 defines it
    }

    @Test
    void a_machine_with_nothing_recorded_has_full_availability_and_zero_oee() {
        long idle = newMachine("IDLE" + SEQ.incrementAndGet());

        OeeDto r = oee.calculateForMachine(idle, day, day);

        assertThat(r.availability()).isEqualByComparingTo("1.0000");
        assertThat(r.performance()).isEqualByComparingTo("0");
        assertThat(r.quality()).isEqualByComparingTo("0");
        assertThat(r.oee()).isEqualByComparingTo("0");
    }

    @Test
    void all_machines_and_the_plant_summary_include_the_machine() {
        worked_example_data();

        assertThat(oee.calculateAll(day, day)).extracting(OeeDto::machineId).contains(machineId);
        OeeDto plant = oee.summary(day, day);
        assertThat(plant.machineCode()).isEqualTo("ALL");
        assertThat(plant.goodQty()).isGreaterThanOrEqualTo(new BigDecimal("400"));
    }

    @Test
    void a_category_outside_the_loss_categories_is_refused_when_recording_downtime() {
        assertThatThrownBy(() -> downtimeService.record(
                new RecordDowntimeRequest(machineId, null, "Tea", "COFFEE_BREAK", null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("COFFEE_BREAK");
    }

    @Test
    void a_range_must_run_forwards_and_stay_within_a_year() {
        assertThatThrownBy(() -> oee.calculateForMachine(machineId, day, day.minusDays(1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("after its end");
        assertThatThrownBy(() -> oee.calculateForMachine(machineId, day.minusDays(400), day))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("at most");
    }

    @Test
    void days_after_today_are_left_out_so_an_unfinished_range_is_not_diluted() {
        OeeDto r = oee.calculateForMachine(machineId, day, LocalDate.now(calendar.zone()).plusDays(30));

        // the range runs to today at most: far fewer than 31 days of shift minutes
        assertThat(r.availableMinutes()).isLessThanOrEqualTo(new BigDecimal(540L * 30));
        assertThat(r.to()).isEqualTo(LocalDate.now(calendar.zone()).plusDays(30));
    }

    @Test
    void downtime_can_be_recorded_with_its_end_in_one_step_and_must_end_after_it_starts() {
        long other = newMachine("REC" + SEQ.incrementAndGet());
        downtimeService.record(new RecordDowntimeRequest(other, null, "Coolant pump", "MINOR_STOP", at(day, 11, 0),
                at(day, 11, 25)));

        assertThat(oee.calculateForMachine(other, day, day).unplannedDowntimeMinutes()).isEqualByComparingTo("25");
        assertThatThrownBy(() -> downtimeService.record(new RecordDowntimeRequest(other, null, "Backwards", "OTHER",
                at(day, 12, 0), at(day, 11, 0)))).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("end after");
    }
}
