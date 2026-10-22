package com.mfg.ims.hrm.domain;

import java.util.EnumSet;
import java.util.Set;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;

/** Leave application state machine: PENDING -> APPROVED | REJECTED | CANCELLED, APPROVED -> CANCELLED. */
public enum LeaveStatus {
    PENDING, APPROVED, REJECTED, CANCELLED;

    public Set<LeaveStatus> next() {
        return switch (this) {
            case PENDING -> EnumSet.of(APPROVED, REJECTED, CANCELLED);
            case APPROVED -> EnumSet.of(CANCELLED);
            case REJECTED, CANCELLED -> EnumSet.noneOf(LeaveStatus.class);
        };
    }

    public LeaveStatus transitionTo(LeaveStatus target) {
        if (!next().contains(target)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A " + this + " leave application cannot become " + target);
        }
        return target;
    }
}
