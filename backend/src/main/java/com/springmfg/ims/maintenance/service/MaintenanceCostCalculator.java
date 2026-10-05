package com.springmfg.ims.maintenance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Labour arithmetic of a maintenance request, kept free of Spring and the database so it can be tested by hand. */
public final class MaintenanceCostCalculator {

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private MaintenanceCostCalculator() {
    }

    /** Hours rounded half-up to 2 decimals, for display. */
    public static BigDecimal labourHours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
    }

    /** Cost = minutes x hourly rate / 60, computed from minutes (not rounded hours) and rounded half-up to 4 decimals. */
    public static BigDecimal labourCost(long minutes, BigDecimal ratePerHour) {
        if (ratePerHour == null) {
            return BigDecimal.ZERO.setScale(4);
        }
        return BigDecimal.valueOf(minutes).multiply(ratePerHour).divide(MINUTES_PER_HOUR, 4, RoundingMode.HALF_UP);
    }
}
