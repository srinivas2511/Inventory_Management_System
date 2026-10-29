package com.mfg.ims.integration.tally;

import java.time.OffsetDateTime;

public record TallyLedgerMappingDto(
        Long id,
        String glAccountCode,
        String tallyLedger,
        String notes,
        OffsetDateTime createdAt) {

    static TallyLedgerMappingDto from(TallyLedgerMapping m) {
        return new TallyLedgerMappingDto(m.getId(), m.getGlAccountCode(),
                m.getTallyLedger(), m.getNotes(), m.getCreatedAt());
    }
}
