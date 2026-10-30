package com.mfg.ims.finance.openitem;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance/party-ledger")
public class PartyLedgerController {

    private final PartyLedgerService partyLedgerService;

    public PartyLedgerController(PartyLedgerService partyLedgerService) {
        this.partyLedgerService = partyLedgerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AR_MANAGE') or hasAuthority('AP_MANAGE')")
    public PartyLedgerDto getPartyLedger(
            @RequestParam String partyType,
            @RequestParam Long partyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return partyLedgerService.getLedger(partyType, partyId, fromDate, toDate);
    }

    public record PartyLedgerDto(
            BigDecimal openingBalance,
            List<LedgerLine> lines,
            BigDecimal closingBalance
    ) {
        public record LedgerLine(
                LocalDate date,
                String reference,
                String description,
                BigDecimal debit,
                BigDecimal credit,
                BigDecimal balance
        ) {}
    }
}
