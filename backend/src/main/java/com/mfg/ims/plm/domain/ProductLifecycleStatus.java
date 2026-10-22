package com.mfg.ims.plm.domain;

import java.util.Optional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;

/** Product lifecycle (PLM-005): DEVELOPMENT -> PROTOTYPE -> APPROVED -> ACTIVE -> PHASE_OUT -> OBSOLETE, one step at a time. */
public enum ProductLifecycleStatus {
    DEVELOPMENT, PROTOTYPE, APPROVED, ACTIVE, PHASE_OUT, OBSOLETE;

    public Optional<ProductLifecycleStatus> next() {
        int i = ordinal() + 1;
        return i < values().length ? Optional.of(values()[i]) : Optional.empty();
    }

    public ProductLifecycleStatus transitionTo(ProductLifecycleStatus target) {
        if (next().filter(n -> n == target).isEmpty()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A " + this + " product can only move to " + next().map(Enum::name).orElse("no further state")
                            + ", not " + target);
        }
        return target;
    }
}
