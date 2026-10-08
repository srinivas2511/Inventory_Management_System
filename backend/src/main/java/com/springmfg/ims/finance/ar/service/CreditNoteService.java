package com.springmfg.ims.finance.ar.service;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.common.security.SegregationOfDuties;
import com.springmfg.ims.finance.ar.domain.ArInvoice;
import com.springmfg.ims.finance.ar.domain.CreditNote;
import com.springmfg.ims.finance.ar.dto.CreditNoteDtos.CreateCreditNoteRequest;
import com.springmfg.ims.finance.ar.dto.CreditNoteDtos.CreditNoteDto;
import com.springmfg.ims.finance.ar.repository.ArInvoiceRepository;
import com.springmfg.ims.finance.ar.repository.ArReceiptRepository;
import com.springmfg.ims.finance.ar.repository.CreditNoteRepository;
import com.springmfg.ims.finance.config.service.ExchangeRateService;
import com.springmfg.ims.finance.gl.dto.AutoJournalRequest;
import com.springmfg.ims.finance.gl.dto.JournalLineRequest;
import com.springmfg.ims.finance.gl.service.GlAccountResolver;
import com.springmfg.ims.finance.gl.service.JournalService;
import com.springmfg.ims.finance.tax.domain.InvoiceTaxLine;
import com.springmfg.ims.finance.tax.repository.InvoiceTaxLineRepository;
import com.springmfg.ims.finance.tax.service.InvoiceTaxService;
import com.springmfg.ims.inventory.domain.FgBatch;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.repository.FgBatchRepository;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.sales.domain.DispatchItem;
import com.springmfg.ims.sales.repository.DispatchItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * AR credit notes (FIN-004). A credit note takes part of an open invoice off what the customer owes: Dr Revenue and the
 * output tax it reverses, Cr AR control. It is raised by one user and approved, which posts it, by another. A credit
 * note may also receive returned finished goods (CUSTOMER_RETURN, into QUARANTINE at the cost the batch carries).
 *
 * <p>A credit is booked at the rate the invoice was booked at, so it never creates an exchange difference, and it
 * cannot exceed the invoice's open balance: giving back money that was already received is not part of this task.
 */
@Service
@Transactional
public class CreditNoteService {

    private static final String AR_CONTROL = "1101";
    private static final String REVENUE = "4001";
    private static final String TYPE = "CREDIT_NOTE";

    private final CreditNoteRepository notes;
    private final ArInvoiceRepository invoices;
    private final ArReceiptRepository receipts;
    private final InvoiceTaxLineRepository taxLines;
    private final InvoiceTaxService invoiceTax;
    private final DispatchItemRepository dispatchItems;
    private final FgBatchRepository fgBatches;
    private final InventoryPostingService posting;
    private final JournalService journals;
    private final GlAccountResolver glAccounts;
    private final DocumentNumberService numbers;
    private final AuditService audit;

    public CreditNoteService(CreditNoteRepository notes, ArInvoiceRepository invoices, ArReceiptRepository receipts,
                             InvoiceTaxLineRepository taxLines, InvoiceTaxService invoiceTax,
                             DispatchItemRepository dispatchItems, FgBatchRepository fgBatches,
                             InventoryPostingService posting, JournalService journals, GlAccountResolver glAccounts,
                             DocumentNumberService numbers, AuditService audit) {
        this.notes = notes;
        this.invoices = invoices;
        this.receipts = receipts;
        this.taxLines = taxLines;
        this.invoiceTax = invoiceTax;
        this.dispatchItems = dispatchItems;
        this.fgBatches = fgBatches;
        this.posting = posting;
        this.journals = journals;
        this.glAccounts = glAccounts;
        this.numbers = numbers;
        this.audit = audit;
    }

    public CreditNoteDto create(CreateCreditNoteRequest req) {
        ArInvoice inv = findInvoice(req.arInvoiceId());
        if (!"OPEN".equals(inv.getStatus()) && !"PARTIALLY_PAID".equals(inv.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Invoice " + inv.getInvoiceNumber() + " is " + inv.getStatus() + ": only an open invoice can be credited");
        }
        BigDecimal subtotal = req.subtotal().setScale(4, RoundingMode.HALF_UP);
        BigDecimal remainingSubtotal = inv.getSubtotal().subtract(notes.sumSubtotal(inv.getId()));
        if (subtotal.compareTo(remainingSubtotal) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A credit of " + subtotal.toPlainString()
                    + " is more than the part of invoice " + inv.getInvoiceNumber() + " not yet credited ("
                    + remainingSubtotal.toPlainString() + ")");
        }
        boolean finalCredit = subtotal.compareTo(remainingSubtotal) == 0;
        BigDecimal tax;
        if (inv.getTaxTotal().signum() == 0) {
            tax = BigDecimal.ZERO;
        } else if (finalCredit) {
            tax = inv.getTaxTotal().subtract(notes.sumTax(inv.getId()));
        } else {
            tax = inv.getTaxTotal().multiply(subtotal).divide(inv.getSubtotal(), 4, RoundingMode.HALF_UP);
        }
        BigDecimal total = subtotal.add(tax);
        BigDecimal open = inv.getTotalAmount().subtract(inv.getReceivedAmount()).subtract(notes.sumCommitted(inv.getId()));
        if (total.compareTo(open) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A credit of " + total.toPlainString()
                    + " is more than the open balance of invoice " + inv.getInvoiceNumber() + " (" + open.toPlainString()
                    + "). Giving back money already received is not supported");
        }
        validateReturn(req, inv);

        CreditNote note = new CreditNote();
        note.setCreditNoteNumber(numbers.next("CRN"));
        note.setArInvoiceId(inv.getId());
        note.setCreditDate(req.creditDate());
        note.setReason(req.reason().trim());
        note.setSubtotal(subtotal);
        note.setTaxTotal(tax);
        note.setTotalAmount(total);
        note.setStatus("DRAFT");
        note.setReturnFgBatchId(req.returnFgBatchId());
        note.setReturnQuantity(req.returnQuantity());
        note.setReturnWarehouseId(req.returnWarehouseId());
        note.setReturnLocationId(req.returnLocationId());
        note = notes.save(note);
        allocateTax(note, inv);
        audit.record("AR_CREDIT_NOTE_CREATED", TYPE, String.valueOf(note.getId()), null, "DRAFT " + total.toPlainString(),
                note.getReason());
        return toDto(note, inv);
    }

    /** Approving posts the credit. The approver is never the user who raised it. */
    public CreditNoteDto approve(Long id) {
        CreditNote note = find(id);
        if (!"DRAFT".equals(note.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Credit note " + note.getCreditNoteNumber() + " is " + note.getStatus() + ", not DRAFT");
        }
        Long actor = currentUserId();
        SegregationOfDuties.requireDifferentUser(actor, note.getCreatedBy(),
                "You raised credit note " + note.getCreditNoteNumber() + "; a different finance user must approve it");
        ArInvoice inv = findInvoice(note.getArInvoiceId());
        BigDecimal balance = inv.getTotalAmount().subtract(inv.getReceivedAmount()).subtract(inv.getCreditedAmount());
        if (note.getTotalAmount().compareTo(balance) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Invoice " + inv.getInvoiceNumber()
                    + " has only " + balance.toPlainString() + " open now, less than this credit of "
                    + note.getTotalAmount().toPlainString());
        }

        BigDecimal rate = inv.getExchangeRate();
        List<JournalLineRequest> taxDebits = invoiceTax.journalLines(TYPE, note.getId(), true, "Output tax reversed", rate);
        BigDecimal taxInr = taxDebits.stream().map(JournalLineRequest::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean settlesInvoice = note.getTotalAmount().compareTo(balance) == 0;
        BigDecimal revenueInr;
        BigDecimal control;
        if (settlesInvoice) {
            // the credit that clears the invoice takes whatever INR is still carried, so rounding leaves no residue
            control = inv.getTotalInr().subtract(receipts.sumCarryingInr(inv.getId()))
                    .subtract(notes.sumPostedCarryingInr(inv.getId()));
            revenueInr = control.subtract(taxInr);
        } else {
            revenueInr = ExchangeRateService.toInr(note.getSubtotal(), rate);
            control = revenueInr.add(taxInr);
        }
        if (revenueInr.signum() <= 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Credit note " + note.getCreditNoteNumber() + " leaves no revenue to reverse");
        }
        List<JournalLineRequest> lines = new ArrayList<>();
        lines.add(new JournalLineRequest(glAccounts.idOf(REVENUE), revenueInr, BigDecimal.ZERO, "Revenue reversed"));
        lines.addAll(taxDebits);
        lines.add(new JournalLineRequest(glAccounts.idOf(AR_CONTROL), BigDecimal.ZERO, control, "AR credited"));
        var journal = journals.postSourceJournal(new AutoJournalRequest(
                "AR_CREDIT_NOTE", "AR", TYPE, note.getId(), note.getCreditDate(),
                "Credit note " + note.getCreditNoteNumber() + " against " + inv.getInvoiceNumber() + foreignNote(inv),
                actor, lines));

        note.setCarryingInr(control);
        note.setJournalHeaderId(journal.id());
        note.setStatus("POSTED");
        note.setApprovedBy(actor);
        note.setApprovedAt(Instant.now());

        if (note.getReturnFgBatchId() != null) {
            FgBatch batch = fgBatches.findById(note.getReturnFgBatchId())
                    .orElseThrow(() -> new NotFoundException("FgBatch", note.getReturnFgBatchId()));
            var result = posting.post(new PostingCommand("CUSTOMER_RETURN", "PRODUCT", null,
                    batch.getProduct().getId(), null, batch.getId(), note.getReturnWarehouseId(),
                    note.getReturnLocationId(), "QUARANTINE", note.getReturnQuantity(), batch.getProduct().getUom(),
                    batch.getUnitCost(), TYPE, note.getId(), note.getCreditNoteNumber(),
                    "Customer return against invoice " + inv.getInvoiceNumber()));
            note.setReturnTransactionNumber(result.transactionNumber());
        }

        inv.setCreditedAmount(inv.getCreditedAmount().add(note.getTotalAmount()));
        BigDecimal open = inv.getTotalAmount().subtract(inv.getReceivedAmount()).subtract(inv.getCreditedAmount());
        if (open.signum() == 0) {
            inv.setStatus(inv.getReceivedAmount().signum() > 0 ? "PAID" : "CREDIT_NOTE");
        } else {
            inv.setStatus("PARTIALLY_PAID");
        }
        invoices.save(inv);
        audit.record("AR_CREDIT_NOTE_POSTED", TYPE, String.valueOf(note.getId()), "DRAFT",
                "POSTED " + note.getTotalAmount().toPlainString(), note.getReason());
        return toDto(notes.save(note), inv);
    }

    public CreditNoteDto cancel(Long id) {
        CreditNote note = find(id);
        if (!"DRAFT".equals(note.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only a draft credit note can be cancelled; " + note.getCreditNoteNumber() + " is " + note.getStatus());
        }
        note.setStatus("CANCELLED");
        audit.record("AR_CREDIT_NOTE_CANCELLED", TYPE, String.valueOf(id), "DRAFT", "CANCELLED", null);
        return toDto(notes.save(note), findInvoice(note.getArInvoiceId()));
    }

    @Transactional(readOnly = true)
    public Page<CreditNoteDto> list(Long invoiceId, Pageable pageable) {
        return notes.search(invoiceId == null ? 0L : invoiceId, pageable)
                .map(n -> toDto(n, findInvoice(n.getArInvoiceId())));
    }

    @Transactional(readOnly = true)
    public CreditNoteDto get(Long id) {
        CreditNote note = find(id);
        return toDto(note, findInvoice(note.getArInvoiceId()));
    }

    // ── internals ──────────────────────────────────────────────────────────────

    private void validateReturn(CreateCreditNoteRequest req, ArInvoice inv) {
        boolean any = req.returnFgBatchId() != null || req.returnQuantity() != null
                || req.returnWarehouseId() != null || req.returnLocationId() != null;
        if (!any) {
            return;
        }
        if (req.returnFgBatchId() == null || req.returnQuantity() == null || req.returnWarehouseId() == null
                || req.returnLocationId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A customer return needs the batch, the quantity, and the warehouse and location that receive it");
        }
        if (inv.getDispatchId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Invoice " + inv.getInvoiceNumber() + " was not raised from a dispatch, so it has no goods to return");
        }
        BigDecimal dispatched = dispatchItems.findByDispatchId(inv.getDispatchId()).stream()
                .filter(i -> req.returnFgBatchId().equals(i.getFgBatchId()))
                .map(DispatchItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (dispatched.signum() == 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Batch " + req.returnFgBatchId() + " was not dispatched on invoice " + inv.getInvoiceNumber());
        }
        BigDecimal alreadyReturned = notes.sumReturned(inv.getId(), req.returnFgBatchId());
        if (req.returnQuantity().add(alreadyReturned).compareTo(dispatched) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Returning " + req.returnQuantity().toPlainString()
                    + " would exceed the " + dispatched.toPlainString() + " of this batch dispatched on the invoice ("
                    + alreadyReturned.toPlainString() + " already on credit notes)");
        }
    }

    /**
     * Splits the tax of the note over the tax lines of the invoice in proportion to each line, the last line taking the
     * rounding remainder, so the lines add up to the note's tax exactly.
     */
    private void allocateTax(CreditNote note, ArInvoice inv) {
        if (note.getTaxTotal().signum() == 0) {
            return;
        }
        List<InvoiceTaxLine> source = invoiceTax.entities("AR_INVOICE", inv.getId());
        if (source.isEmpty()) {
            return;
        }
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < source.size(); i++) {
            InvoiceTaxLine s = source.get(i);
            BigDecimal amount = i == source.size() - 1
                    ? note.getTaxTotal().subtract(allocated)
                    : note.getTaxTotal().multiply(s.getTaxAmount()).divide(inv.getTaxTotal(), 4, RoundingMode.HALF_UP);
            allocated = allocated.add(amount);
            InvoiceTaxLine line = new InvoiceTaxLine();
            line.setDocumentType(TYPE);
            line.setDocumentId(note.getId());
            line.setDirection("SALES");
            line.setSupplyType(s.getSupplyType());
            line.setTaxCodeId(s.getTaxCodeId());
            line.setTaxCode(s.getTaxCode());
            line.setTaxType(s.getTaxType());
            line.setRatePct(s.getRatePct());
            line.setTaxableAmount(note.getSubtotal().multiply(s.getTaxableAmount())
                    .divide(inv.getSubtotal(), 4, RoundingMode.HALF_UP));
            line.setTaxAmount(amount);
            line.setGlAccountId(s.getGlAccountId());
            taxLines.save(line);
        }
    }

    private static String foreignNote(ArInvoice inv) {
        if ("INR".equals(inv.getCurrencyCode())) {
            return "";
        }
        return " (" + inv.getCurrencyCode() + " at " + inv.getExchangeRate().toPlainString() + ")";
    }

    private CreditNote find(Long id) {
        return notes.findById(id).orElseThrow(() -> new NotFoundException("CreditNote", id));
    }

    private ArInvoice findInvoice(Long id) {
        return invoices.findById(id).orElseThrow(() -> new NotFoundException("ArInvoice", id));
    }

    private CreditNoteDto toDto(CreditNote n, ArInvoice inv) {
        return new CreditNoteDto(n.getId(), n.getCreditNoteNumber(), n.getArInvoiceId(), inv.getInvoiceNumber(),
                inv.getCurrencyCode(), n.getCreditDate(), n.getReason(), n.getSubtotal(), n.getTaxTotal(),
                n.getTotalAmount(), n.getStatus(), n.getJournalHeaderId(), n.getCreatedBy(), n.getApprovedBy(),
                n.getApprovedAt(), n.getReturnFgBatchId(), n.getReturnQuantity(), n.getReturnTransactionNumber(),
                invoiceTax.lines(TYPE, n.getId()));
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.springmfg.ims.auth.service.UserPermissionCache.CachedUser user) {
            return user.id();
        }
        return null;
    }
}
