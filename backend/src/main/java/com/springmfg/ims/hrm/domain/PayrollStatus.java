package com.springmfg.ims.hrm.domain;

import java.util.EnumSet;
import java.util.Set;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;

/**
 * Payroll run state machine: DRAFT -> COMPLETED (approved, journal posted) -> PAID. Slip calculation is
 * synchronous, so the PROCESSING value DESIGN lists (and the database still allows) is never entered.
 */
public enum PayrollStatus {
    DRAFT, COMPLETED, PAID;

    public Set<PayrollStatus> next() {
        return switch (this) {
            case DRAFT -> EnumSet.of(COMPLETED);
            case COMPLETED -> EnumSet.of(PAID);
            case PAID -> EnumSet.noneOf(PayrollStatus.class);
        };
    }

    public PayrollStatus transitionTo(PayrollStatus target) {
        if (!next().contains(target)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A " + this + " payroll run cannot become " + target);
        }
        return target;
    }

    /** Attendance and leave for the month are frozen once the run is approved. */
    public boolean locksPeriod() {
        return this == COMPLETED || this == PAID;
    }
}
