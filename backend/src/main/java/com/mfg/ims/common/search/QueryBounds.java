package com.mfg.ims.common.search;

import java.time.Instant;
import java.time.LocalDate;

/**
 * PostgreSQL cannot type a null {@link Instant} or {@link LocalDate} in {@code :x IS NULL OR col >= :x}, so open-ended time ranges are
 * bound as wide sentinel bounds instead (= no filter). Both sit well inside the TIMESTAMPTZ range.
 */
public final class QueryBounds {

    public static final Instant MIN = Instant.parse("1900-01-01T00:00:00Z");
    public static final Instant MAX = Instant.parse("9999-12-31T23:59:59Z");

    public static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    public static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    private QueryBounds() {
    }

    public static Instant from(Instant from) {
        return from == null ? MIN : from;
    }

    public static Instant to(Instant to) {
        return to == null ? MAX : to;
    }

    public static LocalDate from(LocalDate from) {
        return from == null ? MIN_DATE : from;
    }

    public static LocalDate to(LocalDate to) {
        return to == null ? MAX_DATE : to;
    }
}
