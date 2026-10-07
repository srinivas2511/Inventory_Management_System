package com.springmfg.ims.controlling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Collection;

import com.springmfg.ims.production.domain.ProductionOperation;

/**
 * Actual conversion cost of a production order: the elapsed time of each operation at the cost centre rates (DESIGN
 * 16.3). Labour is hours x labour rate, machine cost applies only to operations that ran on a machine, and overhead
 * is a percentage of labour plus machine cost. Operations that were not both started and completed contribute nothing.
 * Pure arithmetic, so the order's completion and the variance journal use the same figures.
 */
public final class ConversionCostCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    /** Cost components at ledger precision; {@code total} is the rounded sum of the unrounded parts. */
    public record Result(BigDecimal hours, BigDecimal labour, BigDecimal machine, BigDecimal overhead, BigDecimal total) {
    }

    private ConversionCostCalculator() {
    }

    public static Result compute(Collection<ProductionOperation> operations, BigDecimal labourRate,
                                 BigDecimal machineRate, BigDecimal overheadPct) {
        BigDecimal hours = BigDecimal.ZERO;
        BigDecimal labour = BigDecimal.ZERO;
        BigDecimal machine = BigDecimal.ZERO;
        for (ProductionOperation op : operations) {
            if (op.getStartedAt() == null || op.getCompletedAt() == null) continue;
            BigDecimal opHours = BigDecimal.valueOf(Duration.between(op.getStartedAt(), op.getCompletedAt()).toSeconds())
                    .divide(SECONDS_PER_HOUR, 6, RoundingMode.HALF_UP);
            hours = hours.add(opHours);
            labour = labour.add(opHours.multiply(labourRate));
            if (op.getMachineId() != null) {
                machine = machine.add(opHours.multiply(machineRate));
            }
        }
        BigDecimal overhead = labour.add(machine).multiply(overheadPct).divide(HUNDRED, 8, RoundingMode.HALF_UP);
        return new Result(hours, scale(labour), scale(machine), scale(overhead), scale(labour.add(machine).add(overhead)));
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(4, RoundingMode.HALF_UP);
    }
}
