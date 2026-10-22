package com.mfg.ims.controlling.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mfg.ims.production.domain.ProductionOperation;

/** Hand-worked fixtures for the actual conversion cost (labour, machine, overhead) of a production order. */
class ConversionCostCalculatorTest {

    private static final Instant T0 = Instant.parse("2026-10-07T08:00:00Z");

    private static ProductionOperation op(Duration length, Long machineId) {
        ProductionOperation op = new ProductionOperation();
        op.setStartedAt(T0);
        op.setCompletedAt(T0.plus(length));
        op.setMachineId(machineId);
        return op;
    }

    private static ConversionCostCalculator.Result compute(List<ProductionOperation> ops, String labour, String machine,
                                                           String overheadPct) {
        return ConversionCostCalculator.compute(ops, new BigDecimal(labour), new BigDecimal(machine),
                new BigDecimal(overheadPct));
    }

    /**
     * 2 h and 30 min without a machine, 1 h on a machine; labour 150 an hour, machine 200 an hour, overhead 10 %.
     * labour (2 + 0.5 + 1) x 150 = 525; machine 1 x 200 = 200; overhead (525 + 200) x 10 % = 72.5; total 797.5.
     */
    @Test
    @DisplayName("CCC-1: labour on every hour, machine cost only on machine hours, overhead on both")
    void labourMachineAndOverhead() {
        var r = compute(List.of(op(Duration.ofHours(2), null), op(Duration.ofMinutes(30), null),
                op(Duration.ofHours(1), 7L)), "150", "200", "10");

        assertThat(r.hours()).isEqualByComparingTo("3.5");
        assertThat(r.labour()).isEqualByComparingTo("525.0000");
        assertThat(r.machine()).isEqualByComparingTo("200.0000");
        assertThat(r.overhead()).isEqualByComparingTo("72.5000");
        assertThat(r.total()).isEqualByComparingTo("797.5000");
    }

    @Test
    @DisplayName("CCC-2: an operation that was not both started and completed contributes nothing")
    void unfinishedOperationsAreIgnored() {
        ProductionOperation neverCompleted = new ProductionOperation();
        neverCompleted.setStartedAt(T0);
        ProductionOperation neverStarted = new ProductionOperation();
        neverStarted.setCompletedAt(T0.plusSeconds(3600));

        var r = compute(List.of(neverCompleted, neverStarted, op(Duration.ofHours(1), null)), "60", "0", "0");

        assertThat(r.hours()).isEqualByComparingTo("1");
        assertThat(r.total()).isEqualByComparingTo("60.0000");
    }

    /** 20 minutes is 0.333333 h (6 dp); at 60 an hour that is 19.99998, which is 20.0000 at ledger precision. */
    @Test
    @DisplayName("CCC-3: hours are kept to 6 decimals and money is rounded half up to 4")
    void rounding() {
        var r = compute(List.of(op(Duration.ofMinutes(20), null)), "60", "0", "0");

        assertThat(r.hours()).isEqualByComparingTo("0.333333");
        assertThat(r.labour()).isEqualByComparingTo("20.0000");
        assertThat(r.total()).isEqualByComparingTo("20.0000");
    }

    @Test
    @DisplayName("CCC-4: with no rate there is no conversion cost, so no absorption journal is expected")
    void zeroRatesGiveZero() {
        var r = compute(List.of(op(Duration.ofHours(5), 7L)), "0", "0", "0");

        assertThat(r.hours()).isEqualByComparingTo("5");
        assertThat(r.total()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("CCC-5: the total is the rounded sum of the unrounded parts, so it can differ from the sum of the rounded parts")
    void totalIsRoundedOnce() {
        // 1 s = 0.000278 h; at 0.5 an hour labour is 0.000139 (0.0001) and overhead at 100 % is another 0.000139
        // (0.0001); the parts show 0.0002 but the unrounded sum 0.000278 rounds to 0.0003
        var r = compute(List.of(op(Duration.ofSeconds(1), null)), "0.5", "0", "100");

        assertThat(r.labour()).isEqualByComparingTo("0.0001");
        assertThat(r.overhead()).isEqualByComparingTo("0.0001");
        assertThat(r.total()).isEqualByComparingTo("0.0003");
    }
}
