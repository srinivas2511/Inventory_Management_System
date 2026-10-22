package com.mfg.ims.qualityplus.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;

/**
 * Control-limit and capability arithmetic (QMS-007, QMS-008, DESIGN 22.2). BigDecimal throughout (CLAUDE.md rule 8);
 * stored results are rounded to 6 places to match NUMERIC(14,6).
 */
public final class SpcMath {

    public static final int SCALE = 6;
    private static final MathContext MC = new MathContext(28, RoundingMode.HALF_UP);
    private static final BigDecimal THREE = BigDecimal.valueOf(3);
    private static final BigDecimal SIX = BigDecimal.valueOf(6);

    /** Shewhart constants A2, D3, D4 for subgroup sizes 2..10. */
    private static final BigDecimal[][] XBAR_R_CONSTANTS = {
            /* n=2 */  { bd("1.880"), bd("0"), bd("3.267") },
            /* n=3 */  { bd("1.023"), bd("0"), bd("2.574") },
            /* n=4 */  { bd("0.729"), bd("0"), bd("2.282") },
            /* n=5 */  { bd("0.577"), bd("0"), bd("2.114") },
            /* n=6 */  { bd("0.483"), bd("0"), bd("2.004") },
            /* n=7 */  { bd("0.419"), bd("0.076"), bd("1.924") },
            /* n=8 */  { bd("0.373"), bd("0.136"), bd("1.864") },
            /* n=9 */  { bd("0.337"), bd("0.184"), bd("1.816") },
            /* n=10 */ { bd("0.308"), bd("0.223"), bd("1.777") },
    };

    private SpcMath() {
    }

    public record Limits(BigDecimal cl, BigDecimal ucl, BigDecimal lcl) {
    }

    public record XbarRLimits(Limits mean, Limits range) {
    }

    public record Capability(BigDecimal cp, BigDecimal cpk, BigDecimal cpu, BigDecimal cpl, BigDecimal mean,
                             BigDecimal stdDev, int count) {
    }

    public static boolean supportsSubgroupSize(int n) {
        return n >= 2 && n <= 10;
    }

    public static BigDecimal mean(List<BigDecimal> values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            sum = sum.add(v);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), MC);
    }

    public static BigDecimal range(List<BigDecimal> values) {
        BigDecimal min = values.get(0);
        BigDecimal max = values.get(0);
        for (BigDecimal v : values) {
            min = min.min(v);
            max = max.max(v);
        }
        return max.subtract(min);
    }

    /** Sample standard deviation (n - 1). */
    public static BigDecimal stdDev(List<BigDecimal> values) {
        if (values.size() < 2) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "At least two measurements are needed for a standard deviation");
        }
        BigDecimal mean = mean(values);
        BigDecimal sumSq = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            BigDecimal d = v.subtract(mean);
            sumSq = sumSq.add(d.multiply(d, MC));
        }
        return sumSq.divide(BigDecimal.valueOf(values.size() - 1L), MC).sqrt(MC);
    }

    public static XbarRLimits xbarR(List<BigDecimal> subgroupMeans, List<BigDecimal> subgroupRanges, int n) {
        if (!supportsSubgroupSize(n)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "X-bar/R charts support subgroup sizes 2 to 10, got " + n);
        }
        BigDecimal[] k = XBAR_R_CONSTANTS[n - 2];
        BigDecimal grandMean = mean(subgroupMeans);
        BigDecimal rBar = mean(subgroupRanges);
        BigDecimal a2r = k[0].multiply(rBar, MC);
        return new XbarRLimits(
                new Limits(round(grandMean), round(grandMean.add(a2r)), round(grandMean.subtract(a2r))),
                new Limits(round(rBar), round(k[2].multiply(rBar, MC)), round(k[1].multiply(rBar, MC))));
    }

    /** P chart centre line from pooled counts; limits are drawn at the average sample size. */
    public static Limits pChart(long totalDefects, long totalSampled, BigDecimal averageSampleSize) {
        if (totalSampled <= 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A P chart needs inspected units");
        }
        BigDecimal pBar = BigDecimal.valueOf(totalDefects).divide(BigDecimal.valueOf(totalSampled), MC);
        Limits at = pLimitsFor(pBar, averageSampleSize);
        return new Limits(round(pBar), at.ucl(), at.lcl());
    }

    /** P chart limits for one point, so charts with varying sample sizes stay honest. Clamped to [0, 1]. */
    public static Limits pLimitsFor(BigDecimal pBar, BigDecimal sampleSize) {
        BigDecimal sigma = pBar.multiply(BigDecimal.ONE.subtract(pBar), MC).divide(sampleSize, MC).sqrt(MC);
        BigDecimal spread = THREE.multiply(sigma, MC);
        return new Limits(round(pBar), round(pBar.add(spread).min(BigDecimal.ONE)),
                round(pBar.subtract(spread).max(BigDecimal.ZERO)));
    }

    public static Limits cChart(List<BigDecimal> counts) {
        BigDecimal cBar = mean(counts);
        BigDecimal spread = THREE.multiply(cBar.sqrt(MC), MC);
        return new Limits(round(cBar), round(cBar.add(spread)), round(cBar.subtract(spread).max(BigDecimal.ZERO)));
    }

    /**
     * Cp, Cpu, Cpl, Cpk from the overall sample standard deviation (DESIGN 22.2). Cp needs both spec limits; with one
     * limit only the matching one-sided index and Cpk are returned.
     */
    public static Capability capability(List<BigDecimal> values, BigDecimal usl, BigDecimal lsl) {
        if (usl == null && lsl == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Capability needs an upper or lower specification limit on the chart");
        }
        BigDecimal sigma = stdDev(values);
        if (sigma.signum() == 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "All measurements are identical, so capability cannot be calculated");
        }
        BigDecimal mean = mean(values);
        BigDecimal threeSigma = THREE.multiply(sigma, MC);
        BigDecimal cpu = usl == null ? null : usl.subtract(mean).divide(threeSigma, MC);
        BigDecimal cpl = lsl == null ? null : mean.subtract(lsl).divide(threeSigma, MC);
        BigDecimal cp = usl != null && lsl != null
                ? usl.subtract(lsl).divide(SIX.multiply(sigma, MC), MC) : null;
        BigDecimal cpk = cpu == null ? cpl : cpl == null ? cpu : cpu.min(cpl);
        return new Capability(round(cp), round(cpk), round(cpu), round(cpl), round(mean), round(sigma),
                values.size());
    }

    public static BigDecimal round(BigDecimal v) {
        return v == null ? null : v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
