package com.springmfg.ims.qualityplus.domain;

import java.util.EnumSet;
import java.util.Set;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;

/**
 * CAPA state machine (QMS-009): OPEN -> ROOT_CAUSE_ANALYSIS -> ACTION_IN_PROGRESS -> VERIFICATION -> CLOSED. A failed
 * verification sends the CAPA back to ACTION_IN_PROGRESS. Closing is a separate, separately authorised step.
 */
public enum CapaStatus {
    OPEN, ROOT_CAUSE_ANALYSIS, ACTION_IN_PROGRESS, VERIFICATION, CLOSED;

    public Set<CapaStatus> next() {
        return switch (this) {
            case OPEN -> EnumSet.of(ROOT_CAUSE_ANALYSIS);
            case ROOT_CAUSE_ANALYSIS -> EnumSet.of(ACTION_IN_PROGRESS);
            case ACTION_IN_PROGRESS -> EnumSet.of(VERIFICATION);
            case VERIFICATION -> EnumSet.of(CLOSED, ACTION_IN_PROGRESS);
            case CLOSED -> EnumSet.noneOf(CapaStatus.class);
        };
    }

    public CapaStatus transitionTo(CapaStatus target) {
        if (!next().contains(target)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A " + this + " CAPA cannot become " + target);
        }
        return target;
    }
}
