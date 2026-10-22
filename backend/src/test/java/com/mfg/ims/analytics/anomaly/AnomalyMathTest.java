package com.mfg.ims.analytics.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * History 10, 12, 11, 13, 9, 12: sum 67, mean 11.1667. Deviations -1.1667, 0.8333, -0.1667, 1.8333, -2.1667, 0.8333;
 * squares 1.3611, 0.6944, 0.0278, 3.3611, 4.6944, 0.6944 = 10.8333; sample variance 10.8333 / 5 = 2.1667;
 * standard deviation 1.4720. A value of 20 is (20 - 11.1667) / 1.4720 = 6.001 deviations above.
 */
class AnomalyMathTest {

    private static final List<Double> HISTORY = List.of(10.0, 12.0, 11.0, 13.0, 9.0, 12.0);

    @Test
    void meanAndSampleStandardDeviation() {
        assertThat(AnomalyMath.mean(HISTORY)).isCloseTo(11.1667, offset(0.0001));
        assertThat(AnomalyMath.sampleStdDev(HISTORY)).isCloseTo(1.4720, offset(0.0001));
    }

    @Test
    void zScoreOfAKnownValue() {
        double sd = AnomalyMath.sampleStdDev(HISTORY);
        assertThat(AnomalyMath.zScore(20, AnomalyMath.mean(HISTORY), sd)).isCloseTo(6.0, offset(0.01));
        assertThat(AnomalyMath.zScore(11.1667, AnomalyMath.mean(HISTORY), sd)).isCloseTo(0.0, offset(0.001));
    }

    @Test
    void aSpikeNeedsTheThreshold() {
        assertThat(AnomalyMath.isSpike(20, HISTORY, 3)).isTrue();
        assertThat(AnomalyMath.isSpike(13, HISTORY, 3)).as("z = 1.25").isFalse();
        assertThat(AnomalyMath.isSpike(5, HISTORY, 3)).as("a low value is not a spike").isFalse();
    }

    @Test
    void aHistoryWithNoSpreadCannotFlagAnything() {
        List<Double> flat = List.of(10.0, 10.0, 10.0, 10.0);
        assertThat(AnomalyMath.sampleStdDev(flat)).isZero();
        assertThat(AnomalyMath.zScore(100, 10, 0)).isNaN();
        assertThat(AnomalyMath.isSpike(100, flat, 3)).isFalse();
    }

    @Test
    void degenerateInputs() {
        assertThat(AnomalyMath.mean(List.of())).isZero();
        assertThat(AnomalyMath.sampleStdDev(List.of(5.0))).isZero();
        assertThat(AnomalyMath.sampleStdDev(List.of())).isZero();
    }

    @Test
    void dropInPercentagePoints() {
        assertThat(AnomalyMath.dropPoints(95, 80)).isEqualTo(15);
        assertThat(AnomalyMath.dropPoints(80, 95)).as("a rise is a negative drop").isEqualTo(-15);
    }
}
