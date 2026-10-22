package com.mfg.ims.crm.domain;

import java.util.EnumSet;
import java.util.Set;

/** Lead state machine (DESIGN section 18): NEW -> CONTACTED -> QUALIFIED -> CONVERTED, or UNQUALIFIED from any open state. */
public enum LeadStatus {
    NEW, CONTACTED, QUALIFIED, UNQUALIFIED, CONVERTED;

    public Set<LeadStatus> next() {
        return switch (this) {
            case NEW -> EnumSet.of(CONTACTED, UNQUALIFIED);
            case CONTACTED -> EnumSet.of(QUALIFIED, UNQUALIFIED);
            case QUALIFIED -> EnumSet.of(CONVERTED, UNQUALIFIED);
            case UNQUALIFIED, CONVERTED -> EnumSet.noneOf(LeadStatus.class);
        };
    }

    public boolean canMoveTo(LeadStatus target) {
        return next().contains(target);
    }

    public boolean isOpen() {
        return this == NEW || this == CONTACTED || this == QUALIFIED;
    }
}
