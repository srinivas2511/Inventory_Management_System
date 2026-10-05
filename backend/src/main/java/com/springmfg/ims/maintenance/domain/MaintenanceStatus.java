package com.springmfg.ims.maintenance.domain;

import java.util.EnumSet;
import java.util.Set;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;

/** Maintenance request state machine: OPEN -> IN_PROGRESS -> COMPLETED, and OPEN or IN_PROGRESS -> CANCELLED. */
public enum MaintenanceStatus {
    OPEN, IN_PROGRESS, COMPLETED, CANCELLED;

    public Set<MaintenanceStatus> next() {
        return switch (this) {
            case OPEN -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(COMPLETED, CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(MaintenanceStatus.class);
        };
    }

    public MaintenanceStatus transitionTo(MaintenanceStatus target) {
        if (!next().contains(target)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A " + this + " maintenance request cannot become " + target);
        }
        return target;
    }

    /** Work (activities, spare parts) can be booked only while the request is still being worked. */
    public boolean acceptsWork() {
        return this == OPEN || this == IN_PROGRESS;
    }

    public static MaintenanceStatus parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Unknown maintenance status: " + value + " (expected OPEN, IN_PROGRESS, COMPLETED or CANCELLED)");
        }
    }
}
