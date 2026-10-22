package com.mfg.ims.plm.domain;

import java.util.EnumSet;
import java.util.Set;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;

/**
 * ECO state machine (PLM-001): DRAFT -> REVIEW -> APPROVED -> IMPLEMENTING -> IMPLEMENTED. A reviewer may return a
 * REVIEW change to DRAFT, and anything not yet implementing may be CANCELLED.
 */
public enum EcoStatus {
    DRAFT, REVIEW, APPROVED, IMPLEMENTING, IMPLEMENTED, CANCELLED;

    public Set<EcoStatus> next() {
        return switch (this) {
            case DRAFT -> EnumSet.of(REVIEW, CANCELLED);
            case REVIEW -> EnumSet.of(DRAFT, APPROVED, CANCELLED);
            case APPROVED -> EnumSet.of(IMPLEMENTING, CANCELLED);
            case IMPLEMENTING -> EnumSet.of(IMPLEMENTED);
            case IMPLEMENTED, CANCELLED -> EnumSet.noneOf(EcoStatus.class);
        };
    }

    public EcoStatus transitionTo(EcoStatus target) {
        if (!next().contains(target)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A " + this + " ECO cannot become " + target);
        }
        return target;
    }
}
