package com.mfg.ims.qualityplus.domain;

import java.util.EnumSet;
import java.util.Set;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;

/** FAI state machine (QMS-010): REQUIRED -> IN_PROGRESS -> PASSED or FAILED. A failed FAI is repeated as a new one. */
public enum FaiStatus {
    REQUIRED, IN_PROGRESS, PASSED, FAILED;

    public Set<FaiStatus> next() {
        return switch (this) {
            case REQUIRED -> EnumSet.of(IN_PROGRESS);
            case IN_PROGRESS -> EnumSet.of(PASSED, FAILED);
            case PASSED, FAILED -> EnumSet.noneOf(FaiStatus.class);
        };
    }

    public FaiStatus transitionTo(FaiStatus target) {
        if (!next().contains(target)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A " + this + " first article inspection cannot become " + target);
        }
        return target;
    }
}
