package com.springmfg.ims.common.numbering;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

/**
 * Hands out business document numbers (DESIGN.md section 1.3, ARCHITECTURE.md section 17).
 * <p>
 * The counter row is incremented with a single {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING}, which takes a
 * row lock held until the caller's transaction ends. Therefore:
 * <ul>
 * <li>two concurrent callers never get the same number;</li>
 * <li>if the document's transaction rolls back, the increment rolls back with it, so numbers of committed documents
 * are gap-free;</li>
 * <li>callers creating the same type of document queue behind each other until commit. That is the price of
 * gap-free numbers and is acceptable at this system's document volumes.</li>
 * </ul>
 * Years and months are taken in the business time zone ({@code ims.business-zone}, default Asia/Kolkata).
 */
@Service
public class DocumentNumberService {

    private static final String NEXT = """
            INSERT INTO number_sequences (prefix, seq_year, last_value) VALUES (?, ?, 1)
            ON CONFLICT (prefix, seq_year) DO UPDATE SET last_value = number_sequences.last_value + 1
            RETURNING last_value""";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId zone;

    DocumentNumberService(JdbcTemplate jdbc, Clock clock, @Value("${ims.business-zone:Asia/Kolkata}") String zone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    /** The next number for today's period. Must be called inside the transaction that creates the document. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentType type) {
        return next(type, LocalDate.now(clock.withZone(zone)));
    }

    /** As {@link #next(DocumentType)} for the period containing {@code date} (back-dated and test use). */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentType type, LocalDate date) {
        Long value = jdbc.queryForObject(NEXT, Long.class, type.prefix(), type.periodKey(date));
        return type.format(date, value);
    }
}
