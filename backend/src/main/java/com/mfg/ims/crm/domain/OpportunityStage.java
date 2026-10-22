package com.mfg.ims.crm.domain;

import java.util.EnumSet;
import java.util.Set;

/** Opportunity stage machine: PROSPECT -> QUALIFIED -> PROPOSAL -> NEGOTIATION -> CLOSED_WON / CLOSED_LOST. */
public enum OpportunityStage {
    PROSPECT(10), QUALIFIED(25), PROPOSAL(50), NEGOTIATION(75), CLOSED_WON(100), CLOSED_LOST(0);

    private final int defaultProbability;

    OpportunityStage(int defaultProbability) {
        this.defaultProbability = defaultProbability;
    }

    public int defaultProbability() {
        return defaultProbability;
    }

    public Set<OpportunityStage> next() {
        return switch (this) {
            case PROSPECT -> EnumSet.of(QUALIFIED, CLOSED_LOST);
            case QUALIFIED -> EnumSet.of(PROPOSAL, CLOSED_LOST);
            case PROPOSAL -> EnumSet.of(NEGOTIATION, CLOSED_WON, CLOSED_LOST);
            case NEGOTIATION -> EnumSet.of(CLOSED_WON, CLOSED_LOST);
            case CLOSED_WON, CLOSED_LOST -> EnumSet.noneOf(OpportunityStage.class);
        };
    }

    public boolean canMoveTo(OpportunityStage target) {
        return next().contains(target);
    }

    public boolean isOpen() {
        return this != CLOSED_WON && this != CLOSED_LOST;
    }
}
