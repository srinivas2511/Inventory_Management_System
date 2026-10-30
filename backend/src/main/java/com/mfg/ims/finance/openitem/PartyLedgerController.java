package com.mfg.ims.finance.openitem;

import com.mfg.ims.finance.ap.repository.ApInvoiceRepository;
import com.mfg.ims.finance.ap.repository.ApPaymentRepository;
import com.mfg.ims.finance.ar.repository.ArInvoiceRepository;
import com.mfg.ims.finance.ar.repository.ArReceiptRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/finance/party-ledger")
public class PartyLedgerController {

    private final ArInvoiceRepository arInvoiceRepo;
    private final ArReceiptRepository arReceiptRepo;
    private final ApInvoiceRepository apInvoiceRepo;
    private final ApPaymentRepository apPaymentRepo;

    public PartyLedgerController(ArInvoiceRepository arInvoiceRepo,
                                  ArReceiptRepository arReceiptRepo,
                                  ApInvoiceRepository apInvoiceRepo,
                                  ApPaymentRepository apPaymentRepo) {
        this.arInvoiceRepo = arInvoiceRepo;
        this.arReceiptRepo = arReceiptRepo;
        this.apInvoiceRepo = apInvoiceRepo;
        this.apPaymentRepo = apPaymentRepo;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AR_MANAGE') or hasAuthority('AP_MANAGE')")
    public PartyLedgerDto getPartyLedger(
            @RequestParam String partyType,
            @RequestParam Long partyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        LocalDate effectiveFrom = fromDate != null ? fromDate : LocalDate.of(2000, 1, 1);
        LocalDate effectiveTo = toDate != null ? toDate : LocalDate.now();

        List<PartyLedgerDto.LedgerLine> lines = new ArrayList<>();

        if ("CUSTOMER".equalsIgnoreCase(partyType)) {
            // AR invoices = debit entries
            arInvoiceRepo.findByCustomerIdAndStatusIn(partyId,
                            List.of("OPEN", "PARTIALLY_PAID", "PAID"))
                    .stream()
                    .filter(inv -> !inv.getInvoiceDate().isBefore(effectiveFrom)
                            && !inv.getInvoiceDate().isAfter(effectiveTo))
                    .forEach(inv -> lines.add(new PartyLedgerDto.LedgerLine(
                            inv.getInvoiceDate(), inv.getInvoiceNumber(),
                            "Invoice", inv.getTotalAmount(), BigDecimal.ZERO, BigDecimal.ZERO)));

            // AR receipts = credit entries
            arInvoiceRepo.findByCustomerIdAndStatusIn(partyId, List.of("OPEN", "PARTIALLY_PAID", "PAID"))
                    .forEach(inv -> arReceiptRepo.findByArInvoiceId(inv.getId())
                            .stream()
                            .filter(r -> !r.getReceiptDate().isBefore(effectiveFrom)
                                    && !r.getReceiptDate().isAfter(effectiveTo))
                            .forEach(r -> lines.add(new PartyLedgerDto.LedgerLine(
                                    r.getReceiptDate(),
                                    inv.getInvoiceNumber(),
                                    "Receipt",
                                    BigDecimal.ZERO, r.getCarryingInr(), BigDecimal.ZERO))));
        } else if ("SUPPLIER".equalsIgnoreCase(partyType)) {
            // AP invoices = credit entries
            apInvoiceRepo.findBySupplierIdAndStatusIn(partyId,
                            List.of("OPEN", "PARTIALLY_PAID", "PAID"))
                    .stream()
                    .filter(inv -> !inv.getInvoiceDate().isBefore(effectiveFrom)
                            && !inv.getInvoiceDate().isAfter(effectiveTo))
                    .forEach(inv -> lines.add(new PartyLedgerDto.LedgerLine(
                            inv.getInvoiceDate(), inv.getInvoiceNumber(),
                            "Invoice", BigDecimal.ZERO, inv.getTotalAmount(), BigDecimal.ZERO)));

            // AP payments = debit entries
            apInvoiceRepo.findBySupplierIdAndStatusIn(partyId, List.of("OPEN", "PARTIALLY_PAID", "PAID"))
                    .forEach(inv -> apPaymentRepo.findByApInvoiceId(inv.getId())
                            .stream()
                            .filter(p -> !p.getPaymentDate().isBefore(effectiveFrom)
                                    && !p.getPaymentDate().isAfter(effectiveTo))
                            .forEach(p -> lines.add(new PartyLedgerDto.LedgerLine(
                                    p.getPaymentDate(),
                                    inv.getInvoiceNumber(),
                                    "Payment",
                                    p.getCarryingInr(), BigDecimal.ZERO, BigDecimal.ZERO))));
        }

        lines.sort(Comparator.comparing(PartyLedgerDto.LedgerLine::date));

        // Compute running balance
        BigDecimal balance = BigDecimal.ZERO;
        List<PartyLedgerDto.LedgerLine> withBalance = new ArrayList<>();
        for (var line : lines) {
            balance = balance.add(line.debit()).subtract(line.credit());
            withBalance.add(new PartyLedgerDto.LedgerLine(
                    line.date(), line.reference(), line.description(),
                    line.debit(), line.credit(), balance));
        }

        return new PartyLedgerDto(BigDecimal.ZERO, withBalance, balance);
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
