package com.mfg.ims.finance.ar.service;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.event.InvoicePaidEvent;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.finance.ar.domain.ArInvoice;
import com.mfg.ims.finance.ar.domain.ArReceipt;
import com.mfg.ims.finance.ar.dto.ArAgeingRow;
import com.mfg.ims.finance.ar.dto.ArInvoiceDto;
import com.mfg.ims.finance.ar.dto.ArReceiptRequest;
import com.mfg.ims.finance.ar.repository.ArInvoiceRepository;
import com.mfg.ims.finance.ar.repository.ArReceiptRepository;
import com.mfg.ims.finance.config.service.ExchangeRateService;
import com.mfg.ims.finance.gl.dto.AutoJournalRequest;
import com.mfg.ims.finance.gl.dto.JournalLineRequest;
import com.mfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.mfg.ims.finance.gl.service.GlAccountResolver;
import com.mfg.ims.finance.gl.service.JournalService;
import com.mfg.ims.finance.tax.repository.ProductTaxSlabRepository;
import com.mfg.ims.finance.tax.service.InvoiceTaxService;
import com.mfg.ims.finance.tax.service.TaxDeterminationService;
import com.mfg.ims.finance.tax.service.TaxDeterminationService.Direction;
import com.mfg.ims.partner.domain.Customer;
import com.mfg.ims.partner.repository.CustomerRepository;
import com.mfg.ims.sales.domain.Dispatch;
import com.mfg.ims.sales.domain.DispatchItem;
import com.mfg.ims.sales.repository.DispatchItemRepository;
import com.mfg.ims.sales.repository.DispatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class ArService {

    // Chart-of-accounts codes seeded in V15: 1101 Accounts Receivable Control, 4001 Sales Revenue, 1201 Main Bank.
    private static final String AR_CONTROL = "1101";
    private static final String REVENUE = "4001";
    private static final String BANK = "1201";
    private static final String FX_GAIN = "4901";
    private static final String FX_LOSS = "5208";

    private final ApplicationEventPublisher events;
    private final ArInvoiceRepository arInvoiceRepo;
    private final ArReceiptRepository arReceiptRepo;
    private final DispatchRepository dispatchRepo;
    private final DispatchItemRepository dispatchItemRepo;
    private final FiscalPeriodRepository periodRepo;
    private final JournalService journalService;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final CustomerRepository customerRepo;
    private final GlAccountResolver glAccounts;
    private final TaxDeterminationService taxDetermination;
    private final InvoiceTaxService invoiceTax;
    private final ProductTaxSlabRepository productSlabs;
    private final ExchangeRateService fx;
    private final com.mfg.ims.finance.ar.repository.CreditNoteRepository creditNotes;

    public ArService(ArInvoiceRepository arInvoiceRepo,
                     ArReceiptRepository arReceiptRepo,
                     DispatchRepository dispatchRepo,
                     DispatchItemRepository dispatchItemRepo,
                     FiscalPeriodRepository periodRepo,
                     JournalService journalService,
                     DocumentNumberService numberService,
                     AuditService auditService,
                     CustomerRepository customerRepo,
                     GlAccountResolver glAccounts,
                     ApplicationEventPublisher events,
                     TaxDeterminationService taxDetermination,
                     InvoiceTaxService invoiceTax,
                     ProductTaxSlabRepository productSlabs,
                     ExchangeRateService fx,
                     com.mfg.ims.finance.ar.repository.CreditNoteRepository creditNotes) {
        this.arInvoiceRepo = arInvoiceRepo;
        this.arReceiptRepo = arReceiptRepo;
        this.dispatchRepo = dispatchRepo;
        this.dispatchItemRepo = dispatchItemRepo;
        this.periodRepo = periodRepo;
        this.journalService = journalService;
        this.numberService = numberService;
        this.auditService = auditService;
        this.customerRepo = customerRepo;
        this.glAccounts = glAccounts;
        this.events = events;
        this.taxDetermination = taxDetermination;
        this.invoiceTax = invoiceTax;
        this.productSlabs = productSlabs;
        this.fx = fx;
        this.creditNotes = creditNotes;
    }

    /**
     * Invoices a dispatch: one invoice per dispatch, created together with its Dr AR Control / Cr Revenue journal in a
     * transaction of its own. It runs after the dispatch has committed (and from the reconciliation retry), so a failure
     * here rolls back only the invoice and never the dispatch. A dispatch worth nothing (free samples) gets a settled
     * zero-value invoice and no journal, because there is nothing to account for.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ArInvoiceDto autoCreateFromDispatch(Long dispatchId) {
        var existing = arInvoiceRepo.findByDispatchId(dispatchId);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        Dispatch dispatch = dispatchRepo.findById(dispatchId)
                .orElseThrow(() -> new NotFoundException("Dispatch", dispatchId));

        LocalDate invoiceDate = dispatch.getDispatchDate();
        var period = periodRepo.findOpenPeriodForDate(invoiceDate)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.PERIOD_CLOSED,
                        "No open period for " + invoiceDate));

        BigDecimal invoiceValue = computeDispatchValue(dispatchId);
        boolean worthNothing = invoiceValue.signum() == 0;
        Customer customer = customerRepo.findById(dispatch.getCustomerId())
                .orElseThrow(() -> new NotFoundException("Customer", dispatch.getCustomerId()));
        String currency = customer.getCurrencyCode();
        BigDecimal rate = worthNothing ? BigDecimal.ONE : fx.rateFor(currency, invoiceDate);
        var determination = worthNothing ? null : determineTax(dispatchId, dispatch.getCustomerId());
        BigDecimal tax = determination != null ? determination.taxTotal() : BigDecimal.ZERO;

        ArInvoice inv = new ArInvoice();
        inv.setInvoiceNumber(numberService.next("ARINV"));
        inv.setCustomerId(dispatch.getCustomerId());
        inv.setDispatchId(dispatchId);
        inv.setInvoiceDate(invoiceDate);
        inv.setDueDate(invoiceDate.plusDays(30));
        inv.setSubtotal(invoiceValue);
        inv.setTaxTotal(tax);
        inv.setTotalAmount(invoiceValue.add(tax));
        if (determination != null) {
            inv.setSupplyType(determination.supplyType());
        }
        inv.setReceivedAmount(BigDecimal.ZERO);
        inv.setCurrencyCode(currency);
        inv.setExchangeRate(rate);
        inv.setStatus(worthNothing ? "PAID" : "OPEN");
        inv.setPeriodId(period.getId());
        inv = arInvoiceRepo.save(inv);
        if (determination != null) {
            invoiceTax.record("AR_INVOICE", inv.getId(), Direction.SALES, determination);
        }

        if (!worthNothing) {
            List<JournalLineRequest> credits = new java.util.ArrayList<>();
            credits.add(new JournalLineRequest(glAccounts.idOf(REVENUE), BigDecimal.ZERO,
                    ExchangeRateService.toInr(invoiceValue, rate), "Revenue Cr"));
            if (determination != null) {
                credits.addAll(invoiceTax.journalLines("AR_INVOICE", inv.getId(), false, "Output tax", rate));
            }
            // An INR invoice debits its total; a foreign one debits the sum of its converted credits, so the journal
            // balances whatever the rounding of the individual conversions.
            BigDecimal receivable = rate.compareTo(BigDecimal.ONE) == 0
                    ? inv.getTotalAmount()
                    : credits.stream().map(JournalLineRequest::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<JournalLineRequest> journalLines = new java.util.ArrayList<>();
            journalLines.add(new JournalLineRequest(glAccounts.idOf(AR_CONTROL), receivable, BigDecimal.ZERO, "AR Dr"));
            journalLines.addAll(credits);
            inv.setTotalInr(receivable);
            var jnlReq = new AutoJournalRequest(
                    "AR_INVOICE", "SALES", "DISPATCH", dispatchId,
                    invoiceDate,
                    "AR Invoice " + inv.getInvoiceNumber() + " for dispatch " + dispatch.getDispatchNumber()
                            + foreignNote(inv.getCurrencyCode(), inv.getTotalAmount(), rate),
                    currentUserId(),
                    journalLines
            );
            // Same transaction as the invoice: an invoice without its journal (or the reverse) must never be committed.
            inv.setJournalHeaderId(journalService.postSourceJournal(jnlReq).id());
        }

        auditService.record("AR_INVOICE_CREATED", "AR_INVOICE", String.valueOf(inv.getId()),
                null, worthNothing ? "PAID (zero value)" : "OPEN", null);
        return toDto(arInvoiceRepo.save(inv));
    }

    public ArInvoiceDto recordReceipt(Long invoiceId, ArReceiptRequest req) {
        ArInvoice inv = findInvoice(invoiceId);
        if (!"OPEN".equals(inv.getStatus()) && !"PARTIALLY_PAID".equals(inv.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Invoice " + inv.getInvoiceNumber() + " is not receivable");
        }
        BigDecimal balance = inv.getTotalAmount().subtract(inv.getReceivedAmount()).subtract(inv.getCreditedAmount());
        if (req.amount().compareTo(balance) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Receipt amount exceeds outstanding balance of " + balance);
        }

        // Settlement: the receivable leaves at the rate the invoice was booked at, the bank receives at the rate of the
        // receipt date, and the difference is the realised exchange gain or loss. The last receipt clears whatever INR
        // is still carried, so rounding never leaves a residue on the receivable.
        BigDecimal receiptRate = fx.rateFor(inv.getCurrencyCode(), req.receiptDate());
        boolean settles = req.amount().compareTo(balance) == 0;
        BigDecimal carrying = settles
                ? inv.getTotalInr().subtract(arReceiptRepo.sumCarryingInr(invoiceId))
                        .subtract(creditNotes.sumPostedCarryingInr(invoiceId))
                : ExchangeRateService.toInr(req.amount(), inv.getExchangeRate());
        BigDecimal receivedInr = ExchangeRateService.toInr(req.amount(), receiptRate);
        BigDecimal difference = receivedInr.subtract(carrying);

        List<JournalLineRequest> receiptLines = new java.util.ArrayList<>();
        receiptLines.add(new JournalLineRequest(glAccounts.idOf(BANK), receivedInr, BigDecimal.ZERO, "Receipt Dr"));
        if (difference.signum() < 0) {
            receiptLines.add(new JournalLineRequest(glAccounts.idOf(FX_LOSS), difference.negate(), BigDecimal.ZERO,
                    "Realised exchange loss"));
        }
        receiptLines.add(new JournalLineRequest(glAccounts.idOf(AR_CONTROL), BigDecimal.ZERO, carrying, "Receipt Cr"));
        if (difference.signum() > 0) {
            receiptLines.add(new JournalLineRequest(glAccounts.idOf(FX_GAIN), BigDecimal.ZERO, difference,
                    "Realised exchange gain"));
        }
        var jnlReq = new AutoJournalRequest(
                "AR_RECEIPT", "AR", "CUSTOMER_RECEIPT", invoiceId,
                req.receiptDate(),
                "AR Receipt for " + inv.getInvoiceNumber() + foreignNote(inv.getCurrencyCode(), req.amount(), receiptRate),
                currentUserId(),
                receiptLines
        );
        var jnl = journalService.postSourceJournal(jnlReq);

        ArReceipt receipt = new ArReceipt();
        receipt.setReceiptNumber(numberService.next("ARRCT"));
        receipt.setArInvoiceId(invoiceId);
        receipt.setReceiptDate(req.receiptDate());
        receipt.setAmount(req.amount());
        receipt.setPaymentMode(req.paymentMode() != null ? req.paymentMode() : "BANK_TRANSFER");
        receipt.setReferenceNumber(req.referenceNumber());
        receipt.setJournalHeaderId(jnl.id());
        receipt.setExchangeRate(receiptRate);
        receipt.setCarryingInr(carrying);
        receipt.setFxDifference(difference);
        arReceiptRepo.save(receipt);

        BigDecimal newReceived = inv.getReceivedAmount().add(req.amount());
        inv.setReceivedAmount(newReceived);
        boolean settled = newReceived.add(inv.getCreditedAmount()).compareTo(inv.getTotalAmount()) >= 0;
        inv.setStatus(settled ? "PAID" : "PARTIALLY_PAID");
        ArInvoice saved = arInvoiceRepo.save(inv);
        if (settled) {
            events.publishEvent(new InvoicePaidEvent(saved.getId(), saved.getInvoiceNumber(), saved.getCustomerId(),
                    saved.getTotalAmount(), saved.getCurrencyCode()));
        }
        return toDto(saved);
    }

    /** " (USD 1,000.00 at 83.250000)" for a foreign document, nothing for INR. */
    private static String foreignNote(String currency, BigDecimal amount, BigDecimal rate) {
        if (currency == null || "INR".equals(currency)) {
            return "";
        }
        return " (" + currency + " " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " at "
                + rate.toPlainString() + ")";
    }

    @Transactional(readOnly = true)
    public Page<ArInvoiceDto> findAll(Pageable pageable) {
        return arInvoiceRepo.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<ArAgeingRow> getAgeing() {
        List<ArInvoice> openInvoices = arInvoiceRepo.findByStatusIn(List.of("OPEN", "PARTIALLY_PAID"));
        LocalDate today = LocalDate.now();

        Map<Long, List<ArInvoice>> byCustomer = openInvoices.stream()
                .collect(Collectors.groupingBy(ArInvoice::getCustomerId));

        return byCustomer.entrySet().stream().map(entry -> {
            Long customerId = entry.getKey();
            String customerName = customerRepo.findById(customerId)
                    .map(Customer::getName).orElse("Unknown");
            BigDecimal current = BigDecimal.ZERO, d30 = BigDecimal.ZERO,
                       d60 = BigDecimal.ZERO, d90 = BigDecimal.ZERO, d90p = BigDecimal.ZERO;

            for (ArInvoice inv : entry.getValue()) {
                BigDecimal bal = ExchangeRateService.toInr(inv.getTotalAmount().subtract(inv.getReceivedAmount()).subtract(inv.getCreditedAmount()),
                        inv.getExchangeRate());
                long days = ChronoUnit.DAYS.between(inv.getDueDate(), today);
                if (days <= 0) current = current.add(bal);
                else if (days <= 30) d30 = d30.add(bal);
                else if (days <= 60) d60 = d60.add(bal);
                else if (days <= 90) d90 = d90.add(bal);
                else d90p = d90p.add(bal);
            }
            BigDecimal total = current.add(d30).add(d60).add(d90).add(d90p);
            return new ArAgeingRow(customerId, customerName, current, d30, d60, d90, d90p, total);
        }).toList();
    }

    /**
     * The GST of a dispatch: each priced line is taxed under the slab of its product, the lines of one slab together.
     * A dispatch whose products have no slab is invoiced without tax, as before the tax engine.
     */
    private TaxDeterminationService.Determination determineTax(Long dispatchId, Long customerId) {
        Map<String, BigDecimal> bySlab = new LinkedHashMap<>();
        for (DispatchItem item : dispatchItemRepo.findByDispatchId(dispatchId)) {
            if (item.getUnitPrice() == null) {
                continue;
            }
            BigDecimal value = item.getQuantity().multiply(item.getUnitPrice()).setScale(4, RoundingMode.HALF_UP);
            productSlabs.findByProductId(item.getProductId())
                    .ifPresent(p -> bySlab.merge(p.getSlabCode(), value, BigDecimal::add));
        }
        if (bySlab.isEmpty()) {
            return null;
        }
        String gst = customerRepo.findById(customerId).map(Customer::getGstNumber).orElse(null);
        var parts = bySlab.entrySet().stream()
                .map(e -> taxDetermination.determine(Direction.SALES, e.getKey(), gst, e.getValue())).toList();
        return parts.size() == 1 ? parts.get(0) : taxDetermination.combine(parts);
    }

    /** Sum of quantity x unit price per dispatched line, each line rounded to 4 dp half-up (money scale). */
    private BigDecimal computeDispatchValue(Long dispatchId) {
        BigDecimal total = BigDecimal.ZERO;
        for (DispatchItem item : dispatchItemRepo.findByDispatchId(dispatchId)) {
            if (item.getUnitPrice() == null) {
                continue;
            }
            total = total.add(item.getQuantity().multiply(item.getUnitPrice()).setScale(4, RoundingMode.HALF_UP));
        }
        return total;
    }

    private ArInvoice findInvoice(Long id) {
        return arInvoiceRepo.findById(id).orElseThrow(() -> new NotFoundException("ArInvoice", id));
    }

    private ArInvoiceDto toDto(ArInvoice inv) {
        BigDecimal balance = inv.getTotalAmount().subtract(inv.getReceivedAmount()).subtract(inv.getCreditedAmount());
        return new ArInvoiceDto(inv.getId(), inv.getInvoiceNumber(), inv.getCustomerId(),
                inv.getDispatchId(), inv.getInvoiceDate(), inv.getDueDate(),
                inv.getSubtotal(), inv.getTaxTotal(), inv.getTotalAmount(),
                inv.getReceivedAmount(), inv.getCreditedAmount(), balance, inv.getStatus(),
                inv.getPeriodId(), inv.getJournalHeaderId(),
                inv.getSupplyType(), invoiceTax.lines("AR_INVOICE", inv.getId()),
                inv.getCurrencyCode(), inv.getExchangeRate(), inv.getTotalInr());
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.mfg.ims.auth.service.UserPermissionCache.CachedUser user) {
                return user.id();
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    /** The receipts taken against an invoice, oldest first, with the realised exchange difference of each. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<com.mfg.ims.finance.ap.dto.SettlementDto> receipts(Long invoiceId) {
        arInvoiceRepo.findById(invoiceId).orElseThrow(() -> new NotFoundException("ArInvoice", invoiceId));
        return arReceiptRepo.findByArInvoiceId(invoiceId).stream()
                .sorted(java.util.Comparator.comparing(com.mfg.ims.finance.ar.domain.ArReceipt::getReceiptDate)
                        .thenComparing(com.mfg.ims.finance.ar.domain.ArReceipt::getId))
                .map(r -> new com.mfg.ims.finance.ap.dto.SettlementDto(r.getId(), r.getReceiptNumber(),
                        r.getReceiptDate(), r.getAmount(), r.getPaymentMode(), r.getReferenceNumber(),
                        r.getExchangeRate(), r.getCarryingInr(), r.getFxDifference(), r.getJournalHeaderId()))
                .toList();
    }
}
