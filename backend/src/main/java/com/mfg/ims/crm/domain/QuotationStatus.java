package com.mfg.ims.crm.domain;

import java.util.EnumSet;
import java.util.Set;

/** Quotation state machine: DRAFT -> SENT -> ACCEPTED -> CONVERTED; SENT may also be REJECTED; DRAFT/SENT may EXPIRE. */
public enum QuotationStatus {
    DRAFT, SENT, ACCEPTED, REJECTED, EXPIRED, CONVERTED;

    public Set<QuotationStatus> next() {
        return switch (this) {
            case DRAFT -> EnumSet.of(SENT, EXPIRED);
            case SENT -> EnumSet.of(ACCEPTED, REJECTED, EXPIRED);
            case ACCEPTED -> EnumSet.of(CONVERTED);
            case REJECTED, EXPIRED, CONVERTED -> EnumSet.noneOf(QuotationStatus.class);
        };
    }

    public boolean canMoveTo(QuotationStatus target) {
        return next().contains(target);
    }
}
