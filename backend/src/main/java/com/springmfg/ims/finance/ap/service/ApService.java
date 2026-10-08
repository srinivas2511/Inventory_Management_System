package com.springmfg.ims.finance.ap.service;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.common.security.SegregationOfDuties;
import com.springmfg.ims.finance.ap.domain.ApInvoice;
import com.springmfg.ims.finance.ap.domain.ApPayment;
import com.springmfg.ims.finance.ap.dto.ApAgeingRow;
import com.springmfg.ims.finance.ap.dto.ApInvoiceCreateRequest;
import com.springmfg.ims.finance.ap.dto.ApInvoiceDto;
import com.springmfg.ims.finance.ap.dto.ApPaymentRequest;
import com.springmfg.ims.finance.ap.repository.ApInvoiceRepository;
import com.springmfg.ims.finance.ap.repository.ApPaymentRepository;
import com.springmfg.ims.finance.config.service.ExchangeRateService;
import com.springmfg.ims.finance.gl.dto.AutoJournalRequest;
import com.springmfg.ims.finance.gl.dto.JournalLineRequest;
import com.springmfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.springmfg.ims.finance.gl.service.GlAccountResolver;
import com.springmfg.ims.finance.gl.service.JournalService;
import com.springmfg.ims.finance.tax.service.InvoiceTaxService;
import com.springmfg.ims.finance.tax.service.TaxDeterminationService;
import com.springmfg.ims.finance.tax.service.TaxDeterminationService.Direction;
import com.springmfg.ims.partner.domain.Supplier;
import com.springmfg.ims.partner.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ApService {

    // Chart-of-accounts codes seeded in V15: 2401 GR/IR Clearing, 2402 Accounts Payable Control, 1201 Main Bank.
    private static final String GR_IR_CLEARING = "2401";
    private static final String AP_CONTROL = "2402";
    private static final String BANK = "1201";
    private static final String INPUT_TAX = "1301";
    private static final String PURCHASE_PRICE_VARIANCE = "5207";
    private static final String FX_GAIN = "4901";
    private static final String FX_LOSS = "5208";

    private final ApInvoiceRepository apInvoiceRepo;
    private final ApPaymentRepository apPaymentRepo;
    private final FiscalPeriodRepository periodRepo;
    private final JournalService journalService;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final SupplierRepository supplierRepo;
    private final GlAccountResolver glAccounts;
    private final ThreeWayMatchService matchService;
    private final TaxDeterminationService taxDetermination;
    private final InvoiceTaxService invoiceTax;
    private final ExchangeRateService fx;
    private final com.springmfg.ims.finance.ap.repository.ApPaymentRepository paymentRepo;

    public ApService(ApInvoiceRepository apInvoiceRepo,
                     ApPaymentRepository apPaymentRepo,
                     FiscalPeriodRepository periodRepo,
                     JournalService journalService,
                     DocumentNumberService numberService,
                     AuditService auditService,
                     SupplierRepository supplierRepo,
                     GlAccountResolver glAccounts,
                     ThreeWayMatchService matchService,
                     TaxDeterminationService taxDetermination,
                     InvoiceTaxService invoiceTax,
                     ExchangeRateService fx) {
        this.apInvoiceRepo = apInvoiceRepo;
        this.apPaymentRepo = apPaymentRepo;
        this.periodRepo = periodRepo;
        this.journalService = journalService;
        this.numberService = numberService;
        this.auditService = auditService;
        this.supplierRepo = supplierRepo;
        this.glAccounts = glAccounts;
        this.matchService = matchService;
        this.taxDetermination = taxDetermination;
        this.invoiceTax = invoiceTax;
        this.fx = fx;
        this.paymentRepo = apPaymentRepo;
    }

    public ApInvoiceDto createInvoice(ApInvoiceCreateRequest req) {
        var period = periodRepo.findOpenPeriodForDate(req.invoiceDate())
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.PERIOD_CLOSED,
                        "No open period for " + req.invoiceDate()));

        if (req.goodsReceiptId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A supplier invoice must reference the goods receipt it bills: the three-way match is required (rule 18)");
        }
        BigDecimal tax = req.taxTotal() != null ? req.taxTotal() : BigDecimal.ZERO;
        Supplier supplier = supplierRepo.findById(req.supplierId())
                .orElseThrow(() -> new NotFoundException("Supplier", req.supplierId()));
        String currency = req.currencyCode() != null && !req.currencyCode().isBlank()
                ? req.currencyCode() : supplier.getCurrencyCode();
        BigDecimal rate = fx.rateFor(currency, req.invoiceDate());
        TaxDeterminationService.Determination determination = null;
        if (req.taxSlabCode() != null && !req.taxSlabCode().isBlank()) {
            if (tax.signum() != 0) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Give either a tax slab or a tax amount, not both");
            }
            determination = taxDetermination.determine(Direction.PURCHASE, req.taxSlabCode(),
                    supplier.getGstNumber(), req.subtotal());
            tax = determination.taxTotal();
        }
        BigDecimal total = req.subtotal().add(tax);
        // the goods receipt is valued in INR, so the match compares the invoice converted at the rate of its date
        var position = matchService.lockAndRead(req.goodsReceiptId(), req.supplierId(), null);
        var outcome = matchService.evaluate(position, ExchangeRateService.toInr(req.subtotal(), rate),
                ExchangeRateService.toInr(tax, rate));

        ApInvoice inv = new ApInvoice();
        inv.setInvoiceNumber(numberService.next("APINV"));
        inv.setSupplierInvoiceNo(req.supplierInvoiceNo());
        inv.setSupplierId(req.supplierId());
        inv.setInvoiceDate(req.invoiceDate());
        inv.setDueDate(req.dueDate());
        inv.setGoodsReceiptId(req.goodsReceiptId());
        inv.setSubtotal(req.subtotal());
        inv.setTaxTotal(tax);
        inv.setTotalAmount(total);
        inv.setPaidAmount(BigDecimal.ZERO);
        inv.setStatus("DRAFT");
        inv.setPeriodId(period.getId());
        inv.setCurrencyCode(currency);
        inv.setExchangeRate(rate);
        inv.setTotalInr(ExchangeRateService.toInr(req.subtotal(), rate).add(ExchangeRateService.toInr(tax, rate)));
        inv.setMatchStatus(outcome.status());
        inv.setMatchExpectedAmount(outcome.open());
        inv.setMatchClearedAmount(outcome.cleared());
        inv.setMatchVariance(outcome.variance());
        inv.setPriceVarianceInr(priceVariance(ExchangeRateService.toInr(req.subtotal(), rate), outcome.open()));
        inv.setMatchMessage(outcome.message());
        if (determination != null) {
            inv.setTaxSlabCode(determination.slabCode());
            inv.setSupplyType(determination.supplyType());
        }
        inv = apInvoiceRepo.save(inv);
        if (determination != null) {
            invoiceTax.record("AP_INVOICE", inv.getId(), Direction.PURCHASE, determination);
        }
        auditService.record("AP_INVOICE_CREATED", "AP_INVOICE", String.valueOf(inv.getId()),
                null, "DRAFT/" + outcome.status(), outcome.message());
        return toDto(inv);
    }

    public ApInvoiceDto openInvoice(Long id) {
        ApInvoice inv = findInvoice(id);
        if (!"DRAFT".equals(inv.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Invoice " + inv.getInvoiceNumber() + " is not DRAFT");
        }

        if ("EXCEPTION".equals(inv.getMatchStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Invoice " + inv.getInvoiceNumber() + " does not match its goods receipt and cannot be opened. "
                            + inv.getMatchMessage());
        }
        List<JournalLineRequest> journalLines = invoiceJournalLines(inv);
        var jnlReq = new AutoJournalRequest(
                "AP_INVOICE", "AP", "SUPPLIER_INVOICE", inv.getId(),
                inv.getInvoiceDate(),
                "AP Invoice " + inv.getInvoiceNumber() + foreignNote(inv),
                currentUserId(),
                journalLines
        );
        var jnl = journalService.postSourceJournal(jnlReq);

        inv.setTotalInr(journalLines.stream().map(JournalLineRequest::creditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        inv.setStatus("OPEN");
        inv.setJournalHeaderId(jnl.id());
        auditService.record("AP_INVOICE_OPENED", "AP_INVOICE", String.valueOf(id),
                "DRAFT", "OPEN", null);
        return toDto(apInvoiceRepo.save(inv));
    }

    public ApInvoiceDto recordPayment(Long invoiceId, ApPaymentRequest req) {
        ApInvoice inv = findInvoice(invoiceId);
        if (!"OPEN".equals(inv.getStatus()) && !"PARTIALLY_PAID".equals(inv.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Invoice " + inv.getInvoiceNumber() + " is not payable");
        }
        BigDecimal balance = inv.getTotalAmount().subtract(inv.getPaidAmount());
        if (req.amount().compareTo(balance) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Payment amount exceeds outstanding balance of " + balance);
        }

        // Settlement: the payable leaves at the rate the invoice was booked at, the bank is paid at the rate of the
        // payment date, and the difference is the realised exchange gain or loss. The last payment clears whatever INR
        // is still carried, so rounding never leaves a residue on the payable.
        BigDecimal payRate = fx.rateFor(inv.getCurrencyCode(), req.paymentDate());
        boolean settles = req.amount().compareTo(balance) == 0;
        BigDecimal carrying = settles
                ? inv.getTotalInr().subtract(paymentRepo.sumCarryingInr(invoiceId))
                : ExchangeRateService.toInr(req.amount(), inv.getExchangeRate());
        BigDecimal paidInr = ExchangeRateService.toInr(req.amount(), payRate);
        BigDecimal difference = paidInr.subtract(carrying);

        List<JournalLineRequest> paymentLines = new java.util.ArrayList<>();
        paymentLines.add(new JournalLineRequest(glAccounts.idOf(AP_CONTROL), carrying, BigDecimal.ZERO, "Payment Dr"));
        if (difference.signum() > 0) {
            paymentLines.add(new JournalLineRequest(glAccounts.idOf(FX_LOSS), difference, BigDecimal.ZERO,
                    "Realised exchange loss"));
        }
        paymentLines.add(new JournalLineRequest(glAccounts.idOf(BANK), BigDecimal.ZERO, paidInr, "Payment Cr"));
        if (difference.signum() < 0) {
            paymentLines.add(new JournalLineRequest(glAccounts.idOf(FX_GAIN), BigDecimal.ZERO, difference.negate(),
                    "Realised exchange gain"));
        }
        var jnlReq = new AutoJournalRequest(
                "AP_PAYMENT", "AP", "SUPPLIER_PAYMENT", invoiceId,
                req.paymentDate(),
                "AP Payment for " + inv.getInvoiceNumber() + foreignNote(inv, req.amount(), payRate),
                currentUserId(),
                paymentLines
        );
        var jnl = journalService.postSourceJournal(jnlReq);

        ApPayment payment = new ApPayment();
        payment.setPaymentNumber(numberService.next("APPMT"));
        payment.setApInvoiceId(invoiceId);
        payment.setPaymentDate(req.paymentDate());
        payment.setAmount(req.amount());
        payment.setPaymentMode(req.paymentMode() != null ? req.paymentMode() : "BANK_TRANSFER");
        payment.setReferenceNumber(req.referenceNumber());
        payment.setJournalHeaderId(jnl.id());
        payment.setExchangeRate(payRate);
        payment.setCarryingInr(carrying);
        payment.setFxDifference(difference);
        apPaymentRepo.save(payment);

        BigDecimal newPaid = inv.getPaidAmount().add(req.amount());
        inv.setPaidAmount(newPaid);
        inv.setStatus(newPaid.compareTo(inv.getTotalAmount()) >= 0 ? "PAID" : "PARTIALLY_PAID");
        return toDto(apInvoiceRepo.save(inv));
    }

    /**
     * A finance manager approves an invoice that is outside the match tolerance. Never the person who entered it,
     * and always with a reason; the approval and the variance it books are audited with the invoice.
     */
    public ApInvoiceDto overrideMatch(Long id, String reason) {
        ApInvoice inv = findInvoice(id);
        if (!"EXCEPTION".equals(inv.getMatchStatus()) || !"DRAFT".equals(inv.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Invoice " + inv.getInvoiceNumber() + " has no match exception to approve");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A reason is required to approve a match exception");
        }
        Long actor = currentUserId();
        SegregationOfDuties.requireDifferentUser(actor, inv.getCreatedBy(),
                "You entered invoice " + inv.getInvoiceNumber() + "; a different finance manager must approve its variance");
        var position = matchService.lockAndRead(inv.getGoodsReceiptId(), inv.getSupplierId(), inv.getId());
        var settled = ThreeWayMatchCalculator.settle(inv.getSubtotal(), position.value(), position.invoiced());
        inv.setMatchStatus("OVERRIDDEN");
        inv.setMatchExpectedAmount(settled.open());
        inv.setMatchClearedAmount(settled.cleared());
        inv.setMatchVariance(settled.variance());
        inv.setPriceVarianceInr(settled.variance().max(BigDecimal.ZERO));
        inv.setMatchOverrideBy(actor);
        inv.setMatchOverrideAt(java.time.Instant.now());
        inv.setMatchOverrideReason(reason.trim());
        ApInvoice saved = apInvoiceRepo.save(inv);
        auditService.record("AP_MATCH_OVERRIDE", "AP_INVOICE", String.valueOf(id), "EXCEPTION",
                "OVERRIDDEN variance " + settled.variance().toPlainString(), reason.trim());
        return toDto(saved);
    }

    /** Withdraws a draft invoice, which frees its goods receipt to be invoiced again (for example corrected). */
    public ApInvoiceDto cancelInvoice(Long id) {
        ApInvoice inv = findInvoice(id);
        if (!"DRAFT".equals(inv.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only a draft invoice can be cancelled; invoice " + inv.getInvoiceNumber() + " is " + inv.getStatus());
        }
        inv.setStatus("CANCELLED");
        auditService.record("AP_INVOICE_CANCELLED", "AP_INVOICE", String.valueOf(id), "DRAFT", "CANCELLED", null);
        return toDto(apInvoiceRepo.save(inv));
    }

    /**
     * Dr GR/IR for the receipt value the invoice clears, Dr price variance for what it bills above that, Dr input tax
     * for its tax; Cr AP control for the total. An invoice entered before the match existed clears its whole subtotal.
     */
    private List<JournalLineRequest> invoiceJournalLines(ApInvoice inv) {
        boolean legacy = "NOT_REQUIRED".equals(inv.getMatchStatus());
        BigDecimal cleared = legacy ? inv.getSubtotal() : inv.getMatchClearedAmount();
        BigDecimal variance = legacy ? BigDecimal.ZERO : inv.getMatchVariance();
        List<JournalLineRequest> lines = new java.util.ArrayList<>();
        if (cleared.signum() > 0) {
            lines.add(new JournalLineRequest(glAccounts.idOf(GR_IR_CLEARING), cleared, BigDecimal.ZERO, "GR/IR cleared"));
        }
        if (variance.signum() > 0) {
            lines.add(new JournalLineRequest(glAccounts.idOf(PURCHASE_PRICE_VARIANCE), variance, BigDecimal.ZERO,
                    "Purchase price variance"));
        }
        boolean foreign = inv.getExchangeRate().compareTo(BigDecimal.ONE) != 0;
        if (inv.getTaxSlabCode() != null) {
            lines.addAll(invoiceTax.journalLines("AP_INVOICE", inv.getId(), true, "Input tax", inv.getExchangeRate()));
        } else if (inv.getTaxTotal().signum() > 0) {
            lines.add(new JournalLineRequest(glAccounts.idOf(INPUT_TAX),
                    ExchangeRateService.toInr(inv.getTaxTotal(), inv.getExchangeRate()), BigDecimal.ZERO, "Input tax"));
        }
        // An INR invoice credits its total. A foreign invoice credits the sum of its converted debits, so the journal
        // balances exactly whatever the rounding of the individual conversions.
        BigDecimal control = foreign
                ? lines.stream().map(JournalLineRequest::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                : inv.getTotalAmount();
        lines.add(new JournalLineRequest(glAccounts.idOf(AP_CONTROL), BigDecimal.ZERO, control, "AP Invoice Cr"));
        return lines;
    }

    /** " (USD 1,000.00 at 83.250000)" for a foreign invoice, nothing for INR. */
    private static String foreignNote(ApInvoice inv) {
        return foreignNote(inv, inv.getTotalAmount(), inv.getExchangeRate());
    }

    private static String foreignNote(ApInvoice inv, BigDecimal amount, BigDecimal rate) {
        if ("INR".equals(inv.getCurrencyCode())) {
            return "";
        }
        return " (" + inv.getCurrencyCode() + " " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                + " at " + rate.toPlainString() + ")";
    }

    @Transactional(readOnly = true)
    public Page<ApInvoiceDto> findAll(Pageable pageable) {
        return apInvoiceRepo.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<ApAgeingRow> getAgeing() {
        List<ApInvoice> openInvoices = apInvoiceRepo.findByStatusIn(List.of("OPEN", "PARTIALLY_PAID"));
        LocalDate today = LocalDate.now();

        Map<Long, List<ApInvoice>> bySupplier = openInvoices.stream()
                .collect(Collectors.groupingBy(ApInvoice::getSupplierId));

        return bySupplier.entrySet().stream().map(entry -> {
            Long supplierId = entry.getKey();
            String supplierName = supplierRepo.findById(supplierId)
                    .map(Supplier::getName).orElse("Unknown");
            BigDecimal current = BigDecimal.ZERO, d30 = BigDecimal.ZERO,
                       d60 = BigDecimal.ZERO, d90 = BigDecimal.ZERO, d90p = BigDecimal.ZERO;

            for (ApInvoice inv : entry.getValue()) {
                BigDecimal bal = ExchangeRateService.toInr(inv.getTotalAmount().subtract(inv.getPaidAmount()), inv.getExchangeRate());
                long days = ChronoUnit.DAYS.between(inv.getDueDate(), today);
                if (days <= 0) current = current.add(bal);
                else if (days <= 30) d30 = d30.add(bal);
                else if (days <= 60) d60 = d60.add(bal);
                else if (days <= 90) d90 = d90.add(bal);
                else d90p = d90p.add(bal);
            }
            BigDecimal total = current.add(d30).add(d60).add(d90).add(d90p);
            return new ApAgeingRow(supplierId, supplierName, current, d30, d60, d90, d90p, total);
        }).toList();
    }

    private ApInvoice findInvoice(Long id) {
        return apInvoiceRepo.findById(id).orElseThrow(() -> new NotFoundException("ApInvoice", id));
    }

    private ApInvoiceDto toDto(ApInvoice inv) {
        BigDecimal balance = inv.getTotalAmount().subtract(inv.getPaidAmount());
        return new ApInvoiceDto(inv.getId(), inv.getInvoiceNumber(), inv.getSupplierInvoiceNo(),
                inv.getSupplierId(), inv.getInvoiceDate(), inv.getDueDate(),
                inv.getGoodsReceiptId(), inv.getSubtotal(), inv.getTaxTotal(),
                inv.getTotalAmount(), inv.getPaidAmount(), balance, inv.getStatus(),
                inv.getPeriodId(), inv.getJournalHeaderId(),
                inv.getMatchStatus(), inv.getMatchExpectedAmount(), inv.getMatchVariance(), inv.getMatchMessage(),
                inv.getTaxSlabCode(), inv.getSupplyType(), invoiceTax.lines("AP_INVOICE", inv.getId()),
                inv.getCurrencyCode(), inv.getExchangeRate(), inv.getTotalInr(), inv.getPriceVarianceInr());
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.springmfg.ims.auth.service.UserPermissionCache.CachedUser user) {
                return user.id();
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    /**
     * The payments made against an invoice, oldest first. A payment stores paid less carried (positive is a loss), the list
     * reports it as a gain (+) or loss (-) like the receipts do.
     */
    @Transactional(readOnly = true)
    public List<com.springmfg.ims.finance.ap.dto.SettlementDto> payments(Long invoiceId) {
        apInvoiceRepo.findById(invoiceId).orElseThrow(() -> new NotFoundException("ApInvoice", invoiceId));
        return paymentRepo.findByApInvoiceId(invoiceId).stream()
                .sorted(Comparator.comparing(com.springmfg.ims.finance.ap.domain.ApPayment::getPaymentDate)
                        .thenComparing(com.springmfg.ims.finance.ap.domain.ApPayment::getId))
                .map(p -> new com.springmfg.ims.finance.ap.dto.SettlementDto(p.getId(), p.getPaymentNumber(),
                        p.getPaymentDate(), p.getAmount(), p.getPaymentMode(), p.getReferenceNumber(),
                        p.getExchangeRate(), p.getCarryingInr(), p.getFxDifference().negate(), p.getJournalHeaderId()))
                .toList();
    }

    /** What the invoice bills above the value still open on its goods receipt, in INR; a partial invoice has none. */
    private static BigDecimal priceVariance(BigDecimal subtotalInr, BigDecimal open) {
        return subtotalInr.subtract(open).max(BigDecimal.ZERO).setScale(4, java.math.RoundingMode.HALF_UP);
    }
}
