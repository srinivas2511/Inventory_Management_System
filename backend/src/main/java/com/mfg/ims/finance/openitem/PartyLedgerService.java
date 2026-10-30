package com.mfg.ims.finance.openitem;

import com.mfg.ims.finance.ap.repository.ApInvoiceRepository;
import com.mfg.ims.finance.ap.repository.ApPaymentRepository;
import com.mfg.ims.finance.ar.repository.ArInvoiceRepository;
import com.mfg.ims.finance.ar.repository.ArReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PartyLedgerService {

    private final ArInvoiceRepository arInvoiceRepo;
    private final ArReceiptRepository arReceiptRepo;
    private final ApInvoiceRepository apInvoiceRepo;
    private final ApPaymentRepository apPaymentRepo;

    public PartyLedgerService(ArInvoiceRepository arInvoiceRepo,
                              ArReceiptRepository arReceiptRepo,
                              ApInvoiceRepository apInvoiceRepo,
                              ApPaymentRepository apPaymentRepo) {
        this.arInvoiceRepo = arInvoiceRepo;
        this.arReceiptRepo = arReceiptRepo;
        this.apInvoiceRepo = apInvoiceRepo;
        this.apPaymentRepo = apPaymentRepo;
    }

    @Transactional(readOnly = true)
    public PartyLedgerController.PartyLedgerDto getLedger(String partyType, Long partyId,
                                                           LocalDate fromDate, LocalDate toDate) {
        LocalDate effectiveFrom = fromDate != null ? fromDate : LocalDate.of(2000, 1, 1);
        LocalDate effectiveTo = toDate != null ? toDate : LocalDate.now();

        List<PartyLedgerController.PartyLedgerDto.LedgerLine> lines = new ArrayList<>();

        if ("CUSTOMER".equalsIgnoreCase(partyType)) {
            arInvoiceRepo.findByCustomerIdAndStatusIn(partyId,
                            List.of("OPEN", "PARTIALLY_PAID", "PAID"))
                    .stream()
                    .filter(inv -> !inv.getInvoiceDate().isBefore(effectiveFrom)
                            && !inv.getInvoiceDate().isAfter(effectiveTo))
                    .forEach(inv -> lines.add(new PartyLedgerController.PartyLedgerDto.LedgerLine(
                            inv.getInvoiceDate(), inv.getInvoiceNumber(),
                            "Invoice", inv.getTotalAmount(), BigDecimal.ZERO, BigDecimal.ZERO)));

            arInvoiceRepo.findByCustomerIdAndStatusIn(partyId, List.of("OPEN", "PARTIALLY_PAID", "PAID"))
                    .forEach(inv -> arReceiptRepo.findByArInvoiceId(inv.getId())
                            .stream()
                            .filter(r -> !r.getReceiptDate().isBefore(effectiveFrom)
                                    && !r.getReceiptDate().isAfter(effectiveTo))
                            .forEach(r -> lines.add(new PartyLedgerController.PartyLedgerDto.LedgerLine(
                                    r.getReceiptDate(),
                                    inv.getInvoiceNumber(),
                                    "Receipt",
                                    BigDecimal.ZERO, r.getCarryingInr(), BigDecimal.ZERO))));
        } else if ("SUPPLIER".equalsIgnoreCase(partyType)) {
            apInvoiceRepo.findBySupplierIdAndStatusIn(partyId,
                            List.of("OPEN", "PARTIALLY_PAID", "PAID"))
                    .stream()
                    .filter(inv -> !inv.getInvoiceDate().isBefore(effectiveFrom)
                            && !inv.getInvoiceDate().isAfter(effectiveTo))
                    .forEach(inv -> lines.add(new PartyLedgerController.PartyLedgerDto.LedgerLine(
                            inv.getInvoiceDate(), inv.getInvoiceNumber(),
                            "Invoice", BigDecimal.ZERO, inv.getTotalAmount(), BigDecimal.ZERO)));

            apInvoiceRepo.findBySupplierIdAndStatusIn(partyId, List.of("OPEN", "PARTIALLY_PAID", "PAID"))
                    .forEach(inv -> apPaymentRepo.findByApInvoiceId(inv.getId())
                            .stream()
                            .filter(p -> !p.getPaymentDate().isBefore(effectiveFrom)
                                    && !p.getPaymentDate().isAfter(effectiveTo))
                            .forEach(p -> lines.add(new PartyLedgerController.PartyLedgerDto.LedgerLine(
                                    p.getPaymentDate(),
                                    inv.getInvoiceNumber(),
                                    "Payment",
                                    p.getCarryingInr(), BigDecimal.ZERO, BigDecimal.ZERO))));
        }

        lines.sort(Comparator.comparing(PartyLedgerController.PartyLedgerDto.LedgerLine::date));

        BigDecimal balance = BigDecimal.ZERO;
        List<PartyLedgerController.PartyLedgerDto.LedgerLine> withBalance = new ArrayList<>();
        for (var line : lines) {
            balance = balance.add(line.debit()).subtract(line.credit());
            withBalance.add(new PartyLedgerController.PartyLedgerDto.LedgerLine(
                    line.date(), line.reference(), line.description(),
                    line.debit(), line.credit(), balance));
        }

        return new PartyLedgerController.PartyLedgerDto(BigDecimal.ZERO, withBalance, balance);
    }
}
