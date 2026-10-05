package com.springmfg.ims.oee;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.springmfg.ims.oee.OeeCalculator.DayInput;
import com.springmfg.ims.oee.OeeCalculator.Result;

/** OEE arithmetic checked against figures worked out by hand (DESIGN 24.1). */
class OeeCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 28);

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    private static DayInput day(String planned, String plannedStops, String unplannedStops, String ideal,
                                String badIdeal, String good, String bad) {
        return new DayInput(DAY, n(planned), n(plannedStops), n(unplannedStops), n(ideal), n(badIdeal), n(good), n(bad));
    }

    @Test
    void worked_example_one_shift_with_breakdown_setup_and_rejects() {
        // 9 h shift = 540 min. Breakdown 30 + setup 15 = 45 unplanned -> operating 495.
        // 420 pieces at 1 min each = 420 ideal min, of which 20 rejected. 400 good.
        Result r = OeeCalculator.calculate(List.of(day("540", "0", "45", "420", "20", "400", "20")));

        assertThat(r.operatingMinutes()).isEqualByComparingTo("495");
        assertThat(r.availability()).isEqualByComparingTo("0.9167");   // 495 / 540
        assertThat(r.performance()).isEqualByComparingTo("0.8485");    // 420 / 495
        assertThat(r.quality()).isEqualByComparingTo("0.9524");        // 400 / 420
        assertThat(r.oee()).isEqualByComparingTo("0.7407");            // 400 / 540 = A x P x Q
        assertThat(r.speedLossMinutes()).isEqualByComparingTo("75");   // 495 - 420
    }

    @Test
    void planned_maintenance_is_taken_off_operating_time_as_the_design_formula_says() {
        // 480 available, 60 planned maintenance, 30 breakdown -> 390 operating.
        Result r = OeeCalculator.calculate(List.of(day("480", "60", "30", "390", "0", "390", "0")));

        assertThat(r.operatingMinutes()).isEqualByComparingTo("390");
        assertThat(r.availability()).isEqualByComparingTo("0.8125");   // 390 / 480
        assertThat(r.performance()).isEqualByComparingTo("1.0000");
        assertThat(r.quality()).isEqualByComparingTo("1.0000");
        assertThat(r.oee()).isEqualByComparingTo("0.8125");
    }

    @Test
    void downtime_longer_than_the_shift_is_clamped_and_never_makes_operating_time_negative() {
        // 100 available; 80 planned stop leaves 20; 60 unplanned is cut to 20.
        Result r = OeeCalculator.calculate(List.of(day("100", "80", "60", "0", "0", "0", "0")));

        assertThat(r.plannedDowntimeMinutes()).isEqualByComparingTo("80");
        assertThat(r.unplannedDowntimeMinutes()).isEqualByComparingTo("20");
        assertThat(r.operatingMinutes()).isEqualByComparingTo("0");
        assertThat(r.availability()).isEqualByComparingTo("0");
        assertThat(r.performance()).isEqualByComparingTo("0");
        assertThat(r.oee()).isEqualByComparingTo("0");
    }

    @Test
    void performance_is_capped_at_one_when_output_beats_the_standard_time() {
        // 600 ideal minutes of output in 500 operating minutes would be 120%.
        Result r = OeeCalculator.calculate(List.of(day("500", "0", "0", "600", "0", "600", "0")));

        assertThat(r.performance()).isEqualByComparingTo("1.0000");
        assertThat(r.oee()).isEqualByComparingTo("1.0000");
        assertThat(r.speedLossMinutes()).isEqualByComparingTo("0");
    }

    @Test
    void a_range_is_the_ratio_of_summed_minutes_not_the_average_of_daily_percentages() {
        // Day A: 100 min available, all operating. Day B: 900 available, 450 down.
        // Totals: 1000 available, 550 operating -> 0.55. Averaging the days would give (1 + 0.5) / 2 = 0.75.
        Result r = OeeCalculator.calculate(List.of(
                day("100", "0", "0", "100", "0", "100", "0"),
                day("900", "0", "450", "450", "0", "450", "0")));

        assertThat(r.availableMinutes()).isEqualByComparingTo("1000");
        assertThat(r.operatingMinutes()).isEqualByComparingTo("550");
        assertThat(r.availability()).isEqualByComparingTo("0.5500");
        assertThat(r.performance()).isEqualByComparingTo("1.0000");   // 550 ideal / 550 operating
        assertThat(r.oee()).isEqualByComparingTo("0.5500");
    }

    @Test
    void scrap_and_rejects_both_count_against_quality() {
        // 300 good, 100 bad (rejects + scrap) -> quality 0.75.
        Result r = OeeCalculator.calculate(List.of(day("480", "0", "0", "400", "100", "300", "100")));

        assertThat(r.quality()).isEqualByComparingTo("0.7500");
        assertThat(r.performance()).isEqualByComparingTo("0.8333");   // 400 / 480
        assertThat(r.oee()).isEqualByComparingTo("0.6250");           // 1 x 0.8333 x 0.75
    }

    @Test
    void no_output_gives_zero_quality_and_zero_oee_without_dividing_by_zero() {
        Result r = OeeCalculator.calculate(List.of(day("480", "0", "0", "0", "0", "0", "0")));

        assertThat(r.availability()).isEqualByComparingTo("1.0000");
        assertThat(r.performance()).isEqualByComparingTo("0");
        assertThat(r.quality()).isEqualByComparingTo("0");
        assertThat(r.oee()).isEqualByComparingTo("0");
    }

    @Test
    void an_empty_range_is_all_zero() {
        Result r = OeeCalculator.calculate(List.of());

        assertThat(r.availableMinutes()).isEqualByComparingTo("0");
        assertThat(r.availability()).isEqualByComparingTo("0");
        assertThat(r.oee()).isEqualByComparingTo("0");
    }

    @Test
    void a_day_with_no_planned_time_contributes_nothing_even_with_stray_downtime() {
        Result r = OeeCalculator.calculate(List.of(day("0", "0", "120", "0", "0", "0", "0")));

        assertThat(r.availableMinutes()).isEqualByComparingTo("0");
        assertThat(r.unplannedDowntimeMinutes()).isEqualByComparingTo("0");
    }
}
