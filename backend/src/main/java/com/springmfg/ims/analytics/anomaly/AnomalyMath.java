package com.springmfg.ims.analytics.anomaly;

import java.util.List;

/**
 * The statistics behind the anomaly sweep. These are derived analytics, not stored quantities or money, so plain
 * doubles are used; alert texts round the results.
 */
public final class AnomalyMath {

    private AnomalyMath() {
    }

    public static double mean(List<Double> values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return values.isEmpty() ? 0 : sum / values.size();
    }

    /** Sample standard deviation (n - 1); 0 for fewer than two values. */
    public static double sampleStdDev(List<Double> values) {
        if (values.size() < 2) {
            return 0;
        }
        double m = mean(values);
        double sumSq = 0;
        for (double v : values) {
            sumSq += (v - m) * (v - m);
        }
        return Math.sqrt(sumSq / (values.size() - 1));
    }

    /** How many standard deviations {@code value} lies above {@code mean}; NaN when the history has no spread. */
    public static double zScore(double value, double mean, double stdDev) {
        return stdDev > 0 ? (value - mean) / stdDev : Double.NaN;
    }

    public static boolean isSpike(double value, List<Double> history, double zThreshold) {
        double z = zScore(value, mean(history), sampleStdDev(history));
        return !Double.isNaN(z) && z >= zThreshold;
    }

    /** Percentage-point drop of {@code current} below {@code baseline}, both in percent; negative if it rose. */
    public static double dropPoints(double baselinePct, double currentPct) {
        return baselinePct - currentPct;
    }
}
