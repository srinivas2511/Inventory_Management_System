package com.mfg.ims.finance.ap.service;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.common.security.SegregationOfDuties;
import com.mfg.ims.finance.ap.domain.ApDebitNote;
import com.mfg.ims.finance.ap.domain.ApInvoice;
import com.mfg.ims.finance.ap.dto.ApDebitNoteDtos.ApDebitNoteDto;
import com.mfg.ims.finance.ap.dto.ApDebitNoteDtos.CreateDebitNoteRequest;
import com.mfg.ims.finance.ap.repository.ApDebitNoteRepository;
import com.mfg.ims.finance.ap.repository.ApInvoiceRepository;
import com.mfg.ims.finance.ap.repository.ApPaymentRepository;
import com.mfg.ims.finance.config.service.ExchangeRateService;
import com.mfg.ims.finance.gl.dto.AutoJournalRequest;
import com.mfg.ims.finance.gl.dto.JournalLineRequest;
import com.mfg.ims.finance.gl.service.GlAccountResolver;
import com.mfg.ims.finance.gl.service.JournalService;
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
 * AP debit notes: reduce the amount owed on a supplier invoice (price dispute, supplier overcharge).
 *
 * <p>Raised by AP_MANAGE, approved (which posts it) by a different user with DEBIT_NOTE_APPROVE.
 * Journal on posting: Dr 2402 AP Control / Cr 5207 Purchase Price Variance (subtotal in INR) +
 * Cr 1301 Input Tax Credit (tax in INR, if any) — the mirror of the original invoice entry.
 *
 * <p>Cannot exceed the invoice's open balance (total - paid - already debited).
 */
@Service
@Transactional
public class ApDebitNoteService {

    private static final String AP_CONTROL = "2402";
    private static final String PURCHASE_VARIANCE = "5207";
    private static final String INPUT_TAX = "1301";
    private static final String TYPE = "AP_DEBIT_NOTE";

    private final ApDebitNoteRepository notes;
    private final ApInvoiceRepository invoices;
    private final ApPaymentRepository payments;
    private final JournalService journals;
    private final GlAccountResolver glAccounts;
    private final DocumentNumberService numbers;
    private final AuditService audit;

    public ApDebitNoteService(ApDebitNoteRepository notes, ApInvoiceRepository invoices,
                               ApPaymentRepository payments, JournalService journals,
                               GlAccountResolver glAccounts, DocumentNumberService numbers, AuditService audit) {
        this.notes = notes;
        this.invoices = invoices;
        this.payments = payments;
        this.journals = journals;
        this.glAccounts = glAccounts;
        this.numbers = numbers;
        this.audit = audit;
    }

    public ApDebitNoteDto create(CreateDebitNoteRequest req) {
        ApInvoice inv = findInvoice(req.apInvoiceId());
        if (!"OPEN".equals(inv.getStatus()) && !"PARTIALLY_PAID".equals(inv.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Invoice " + inv.getInvoiceNumber() + " is " + inv.getStatus()
                            + ": only an open invoice can be debited");
        }
        BigDecimal subtotal = req.subtotal().setScale(4, RoundingMode.HALF_UP);
        BigDecimal remainingSubtotal = inv.getSubtotal().subtract(notes.sumSubtotal(inv.getId()));
        if (subtotal.compareTo(remainingSubtotal) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A debit of " + subtotal.toPlainString() + " exceeds the part of invoice "
                            + inv.getInvoiceNumber() + " not yet debited (" + remainingSubtotal.toPlainString() + ")");
        }
        boolean finalDebit = subtotal.compareTo(remainingSubtotal) == 0;
        BigDecimal tax;
        if (inv.getTaxTotal().signum() == 0) {
            tax = BigDecimal.ZERO;
        } else if (finalDebit) {
            tax = inv.getTaxTotal().subtract(notes.sumTax(inv.getId()));
        } else {
            tax = inv.getTaxTotal().multiply(subtotal).divide(inv.getSubtotal(), 4, RoundingMode.HALF_UP);
        }
        BigDecimal total = subtotal.add(tax);
        BigDecimal open = inv.getTotalAmount().subtract(inv.getPaidAmount()).subtract(notes.sumCommitted(inv.getId()));
        if (total.compareTo(open) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A debit of " + total.toPlainString() + " is more than the open balance of invoice "
                            + inv.getInvoiceNumber() + " (" + open.toPlainString() + ")");
        }

        ApDebitNote note = new ApDebitNote();
        note.setDebitNoteNumber(numbers.next("APDN"));
        note.setApInvoiceId(inv.getId());
        note.setDebitDate(req.debitDate());
        note.setReason(req.reason().trim());
        note.setSubtotal(subtotal);
        note.setTaxTotal(tax);
        note.setTotalAmount(total);
        note.setStatus("DRAFT");
        note = notes.save(note);
        audit.record("AP_DEBIT_NOTE_CREATED", TYPE, String.valueOf(note.getId()), null,
                "DRAFT " + total.toPlainString(), note.getReason());
        return toDto(note, inv);
    }

    /** Approving posts the debit. The approver must be a different user from the creator. */
    public ApDebitNoteDto approve(Long id) {
        ApDebitNote note = find(id);
        if (!"DRAFT".equals(note.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Debit note " + note.getDebitNoteNumber() + " is " + note.getStatus() + ", not DRAFT");
        }
        Long actor = currentUserId();
        SegregationOfDuties.requireDifferentUser(actor, note.getCreatedBy(),
                "You raised debit note " + note.getDebitNoteNumber()
                        + "; a different finance user must approve it");

        ApInvoice inv = findInvoice(note.getApInvoiceId());
        BigDecimal open = inv.getTotalAmount().subtract(inv.getPaidAmount()).subtract(inv.getDebitedAmount());
        if (note.getTotalAmount().compareTo(open) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Invoice " + inv.getInvoiceNumber() + " has only " + open.toPlainString()
                            + " open now, less than this debit of " + note.getTotalAmount().toPlainString());
        }

        BigDecimal rate = inv.getExchangeRate();
        boolean settles = note.getTotalAmount().compareTo(open) == 0;
        BigDecimal subtotalInr;
        BigDecimal taxInr;
        BigDecimal controlInr;
        if (settles) {
            // final debit clears whatever INR is still carried, eliminating rounding residue
            controlInr = inv.getTotalInr()
                    .subtract(payments.sumCarryingInr(inv.getId()))
                    .subtract(notes.sumPostedCarryingInr(inv.getId()));
            taxInr = inv.getTaxTotal().signum() == 0 ? BigDecimal.ZERO
                    : ExchangeRateService.toInr(note.getTaxTotal(), rate);
            subtotalInr = controlInr.subtract(taxInr);
        } else {
            subtotalInr = ExchangeRateService.toInr(note.getSubtotal(), rate);
            taxInr = ExchangeRateService.toInr(note.getTaxTotal(), rate);
            controlInr = subtotalInr.add(taxInr);
        }

        List<JournalLineRequest> lines = new ArrayList<>();
        lines.add(new JournalLineRequest(glAccounts.idOf(AP_CONTROL), controlInr, BigDecimal.ZERO, "AP debit Dr"));
        lines.add(new JournalLineRequest(glAccounts.idOf(PURCHASE_VARIANCE), BigDecimal.ZERO, subtotalInr,
                "Purchase debit Cr"));
        if (taxInr.signum() > 0) {
            lines.add(new JournalLineRequest(glAccounts.idOf(INPUT_TAX), BigDecimal.ZERO, taxInr,
                    "Input tax reversed"));
        }
        var journal = journals.postSourceJournal(new AutoJournalRequest(
                "AP_DEBIT_NOTE", "AP", TYPE, note.getId(), note.getDebitDate(),
                "Debit note " + note.getDebitNoteNumber() + " against " + inv.getInvoiceNumber()
                        + foreignNote(inv),
                actor, lines));

        note.setCarryingInr(controlInr);
        note.setJournalHeaderId(journal.id());
        note.setStatus("POSTED");
        note.setApprovedBy(actor);
        note.setApprovedAt(Instant.now());
        notes.save(note);

        inv.setDebitedAmount(inv.getDebitedAmount().add(note.getTotalAmount()));
        BigDecimal remaining = inv.getTotalAmount().subtract(inv.getPaidAmount()).subtract(inv.getDebitedAmount());
        if (remaining.signum() <= 0) {
            inv.setStatus("PAID");
        } else {
            inv.setStatus("PARTIALLY_PAID");
        }
        invoices.save(inv);
        audit.record("AP_DEBIT_NOTE_POSTED", TYPE, String.valueOf(note.getId()), "DRAFT",
                "POSTED " + note.getTotalAmount().toPlainString(), note.getReason());
        return toDto(note, inv);
    }

    public ApDebitNoteDto cancel(Long id) {
        ApDebitNote note = find(id);
        if (!"DRAFT".equals(note.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only a draft debit note can be cancelled; "
                            + note.getDebitNoteNumber() + " is " + note.getStatus());
        }
        note.setStatus("CANCELLED");
        audit.record("AP_DEBIT_NOTE_CANCELLED", TYPE, String.valueOf(id), "DRAFT", "CANCELLED", null);
        return toDto(notes.save(note), findInvoice(note.getApInvoiceId()));
    }

    @Transactional(readOnly = true)
    public Page<ApDebitNoteDto> list(Long invoiceId, Pageable pageable) {
        return notes.search(invoiceId == null ? 0L : invoiceId, pageable)
                .map(n -> toDto(n, findInvoice(n.getApInvoiceId())));
    }

    @Transactional(readOnly = true)
    public ApDebitNoteDto get(Long id) {
        ApDebitNote note = find(id);
        return toDto(note, findInvoice(note.getApInvoiceId()));
    }

    // ── internals ──────────────────────────────────────────────────────────────

    private static String foreignNote(ApInvoice inv) {
        if ("INR".equals(inv.getCurrencyCode())) return "";
        return " (" + inv.getCurrencyCode() + " at " + inv.getExchangeRate().toPlainString() + ")";
    }

    private ApDebitNote find(Long id) {
        return notes.findById(id).orElseThrow(() -> new NotFoundException("ApDebitNote", id));
    }

    private ApInvoice findInvoice(Long id) {
        return invoices.findById(id).orElseThrow(() -> new NotFoundException("ApInvoice", id));
    }

    private ApDebitNoteDto toDto(ApDebitNote n, ApInvoice inv) {
        return new ApDebitNoteDto(n.getId(), n.getDebitNoteNumber(), n.getApInvoiceId(),
                inv.getInvoiceNumber(), inv.getCurrencyCode(), n.getDebitDate(), n.getReason(),
                n.getSubtotal(), n.getTaxTotal(), n.getTotalAmount(), n.getStatus(),
                n.getJournalHeaderId(), n.getCreatedBy(), n.getApprovedBy(), n.getApprovedAt());
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.mfg.ims.auth.service.UserPermissionCache.CachedUser user) {
            return user.id();
        }
        return null;
    }
}
