package com.mfg.ims.finance.tds;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Read/write DTO for a TDS entry.
 * All amounts in INR.
 */
public record TdsEntryDto(
        Long id,
        String direction,
        String partyType,
        Long partyId,
        Long sectionId,
        String tdsSection,
        String tdsSectionDescription,
        BigDecimal baseAmount,
        BigDecimal rate,
        BigDecimal tdsAmount,
        LocalDate deductionDate,
        String challanNo,
        String fiscalYear,
        Instant createdAt
) {
    /** Request-only constructor (no id / createdAt). */
    public static TdsEntryDto request(String direction, String partyType, Long partyId,
                                      Long sectionId, BigDecimal baseAmount, BigDecimal rate,
                                      BigDecimal tdsAmount, LocalDate deductionDate,
                                      String challanNo, String fiscalYear) {
        return new TdsEntryDto(null, direction, partyType, partyId, sectionId,
                null, null, baseAmount, rate, tdsAmount, deductionDate,
                challanNo, fiscalYear, null);
    }
}
