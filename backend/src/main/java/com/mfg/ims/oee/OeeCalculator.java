package com.mfg.ims.oee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * The OEE arithmetic of DESIGN 24.1, free of Spring and the database so it can be checked by hand.
 *
 * <p>Per day the downtime is first clamped to the time available (planned stops first, then unplanned), because a
 * downtime record that spills past a shift must not drive operating time negative. The minutes are then added up over
 * the range and the ratios taken on the totals, so a range is never an average of daily percentages:
 * <ul>
 *   <li>Availability = operating time / planned production time, where operating = planned - planned stops - unplanned stops</li>
 *   <li>Performance  = ideal time of everything produced / operating time, capped at 100%</li>
 *   <li>Quality      = good pieces / all pieces (rejected and scrapped pieces are not good)</li>
 *   <li>OEE          = Availability x Performance x Quality</li>
 * </ul>
 * A ratio whose denominator is zero is 0, never an exception.
 */
public final class OeeCalculator {

    private static final int RATIO_SCALE = 4;
    private static final int WORK_SCALE = 10;

    private OeeCalculator() {
    }

    /** One machine-day: the planned time plus what was recorded against it. */
    public record DayInput(
            LocalDate day,
            BigDecimal plannedMinutes,
            BigDecimal plannedDowntimeMinutes,
            BigDecimal unplannedDowntimeMinutes,
            BigDecimal idealMinutes,
            BigDecimal badIdealMinutes,
            BigDecimal goodQty,
            BigDecimal badQty) {
    }

    public record Result(
            BigDecimal availableMinutes,
            BigDecimal plannedDowntimeMinutes,
            BigDecimal unplannedDowntimeMinutes,
            BigDecimal operatingMinutes,
            BigDecimal idealMinutes,
            BigDecimal badIdealMinutes,
            BigDecimal goodQty,
            BigDecimal badQty,
            BigDecimal availability,
            BigDecimal performance,
            BigDecimal quality,
            BigDecimal oee) {

        /** Operating time not turned into ideal output: the slow-running (speed) loss. */
        public BigDecimal speedLossMinutes() {
            return operatingMinutes.subtract(idealMinutes.min(operatingMinutes)).max(BigDecimal.ZERO);
        }
    }

    public static Result calculate(List<DayInput> days) {
        BigDecimal available = BigDecimal.ZERO;
        BigDecimal plannedStops = BigDecimal.ZERO;
        BigDecimal unplannedStops = BigDecimal.ZERO;
        BigDecimal ideal = BigDecimal.ZERO;
        BigDecimal badIdeal = BigDecimal.ZERO;
        BigDecimal good = BigDecimal.ZERO;
        BigDecimal bad = BigDecimal.ZERO;
        for (DayInput d : days) {
            BigDecimal planned = d.plannedMinutes();
            BigDecimal planStop = d.plannedDowntimeMinutes().min(planned).max(BigDecimal.ZERO);
            BigDecimal unplanStop = d.unplannedDowntimeMinutes().min(planned.subtract(planStop)).max(BigDecimal.ZERO);
            available = available.add(planned);
            plannedStops = plannedStops.add(planStop);
            unplannedStops = unplannedStops.add(unplanStop);
            ideal = ideal.add(d.idealMinutes());
            badIdeal = badIdeal.add(d.badIdealMinutes());
            good = good.add(d.goodQty());
            bad = bad.add(d.badQty());
        }
        BigDecimal operating = available.subtract(plannedStops).subtract(unplannedStops);

        BigDecimal availability = ratio(operating, available);
        BigDecimal performance = ratio(ideal, operating).min(BigDecimal.ONE);
        BigDecimal quality = ratio(good, good.add(bad));
        BigDecimal oee = availability.multiply(performance).multiply(quality).setScale(RATIO_SCALE, RoundingMode.HALF_UP);

        return new Result(available, plannedStops, unplannedStops, operating, ideal, badIdeal, good, bad,
                availability.setScale(RATIO_SCALE, RoundingMode.HALF_UP),
                performance.setScale(RATIO_SCALE, RoundingMode.HALF_UP),
                quality.setScale(RATIO_SCALE, RoundingMode.HALF_UP), oee);
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, WORK_SCALE, RoundingMode.HALF_UP);
    }
}
