package com.springmfg.ims.qualityplus.domain;

import java.util.EnumSet;
import java.util.Set;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;

/** Complaint state machine: OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED; a resolved complaint may be reopened. */
public enum ComplaintStatus {
    OPEN, IN_PROGRESS, RESOLVED, CLOSED;

    public Set<ComplaintStatus> next() {
        return switch (this) {
            case OPEN -> EnumSet.of(IN_PROGRESS);
            case IN_PROGRESS -> EnumSet.of(RESOLVED);
            case RESOLVED -> EnumSet.of(CLOSED, IN_PROGRESS);
            case CLOSED -> EnumSet.noneOf(ComplaintStatus.class);
        };
    }

    public ComplaintStatus transitionTo(ComplaintStatus target) {
        if (!next().contains(target)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A " + this + " complaint cannot become " + target);
        }
        return target;
    }
}
