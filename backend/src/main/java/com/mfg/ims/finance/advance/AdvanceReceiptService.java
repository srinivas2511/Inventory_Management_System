package com.mfg.ims.finance.advance;

import com.mfg.ims.finance.gl.dto.AutoJournalRequest;
import com.mfg.ims.finance.gl.dto.JournalLineRequest;
import com.mfg.ims.finance.gl.service.GlAccountResolver;
import com.mfg.ims.finance.gl.service.JournalService;
import com.mfg.ims.finance.invoice.SalesInvoice;
import com.mfg.ims.finance.invoice.SalesInvoiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class AdvanceReceiptService {

    private final AdvanceReceiptRepository repo;
    private final SalesInvoiceRepository invoiceRepo;
    private final JournalService journalService;
    private final GlAccountResolver glAccountResolver;

    public AdvanceReceiptService(AdvanceReceiptRepository repo,
                                 SalesInvoiceRepository invoiceRepo,
                                 JournalService journalService,
                                 GlAccountResolver glAccountResolver) {
        this.repo = repo;
        this.invoiceRepo = invoiceRepo;
        this.journalService = journalService;
        this.glAccountResolver = glAccountResolver;
    }

    @Transactional(readOnly = true)
    public List<AdvanceReceiptDto> list(Long customerId) {
        List<AdvanceReceipt> receipts = customerId != null
                ? repo.findByCompanyIdAndCustomerIdOrderByReceiptDateDesc(1L, customerId)
                : repo.findByCompanyIdOrderByReceiptDateDesc(1L);
        return receipts.stream().map(this::toDto).toList();
    }

    public AdvanceReceiptDto create(AdvanceReceiptRequest req, Long userId) {
        var receipt = new AdvanceReceipt();
        receipt.setCustomerId(req.customerId());
        receipt.setReceiptDate(req.receiptDate());
        receipt.setAmount(req.amount());
        receipt.setCurrencyCode(req.currencyCode() != null ? req.currencyCode() : "INR");
        receipt.setReference(req.reference());
        receipt.setNotes(req.notes());
        receipt.setCreatedBy(userId);

        // Post GL: Dr 1201 Cash/Bank / Cr 2403 Customer Advance
        Long cashAccountId = glAccountResolver.idOf("1201");
        Long advanceAccountId = glAccountResolver.idOf("2403");

        var journalReq = new AutoJournalRequest(
                "ADVANCE_RECEIPT",
                "FINANCE",
                "ADVANCE_RECEIPT",
                null,
                req.receiptDate(),
                "Advance receipt from customer #" + req.customerId()
                        + (req.reference() != null ? " ref: " + req.reference() : ""),
                userId,
                List.of(
                        new JournalLineRequest(cashAccountId, req.amount(), BigDecimal.ZERO, "Advance received"),
                        new JournalLineRequest(advanceAccountId, BigDecimal.ZERO, req.amount(), "Customer advance liability")
                )
        );
        var journal = journalService.postSourceJournal(journalReq);
        receipt.setJournalId(journal.id());

        return toDto(repo.save(receipt));
    }

    public AdvanceReceiptDto applyToInvoice(Long receiptId, Long invoiceId, BigDecimal amount, Long userId) {
        var receipt = repo.findById(receiptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Advance receipt not found: " + receiptId));
        if (!"OPEN".equals(receipt.getStatus()) && !"PARTIAL".equals(receipt.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Advance receipt is already " + receipt.getStatus());
        }

        BigDecimal available = receipt.getAmount().subtract(receipt.getAppliedAmount());
        if (amount.compareTo(available) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Apply amount " + amount + " exceeds available balance " + available);
        }

        SalesInvoice invoice = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Sales invoice not found: " + invoiceId));

        // Reduce advance applied_amount, reduce invoice outstanding
        receipt.setAppliedAmount(receipt.getAppliedAmount().add(amount));
        if (receipt.getAppliedAmount().compareTo(receipt.getAmount()) >= 0) {
            receipt.setStatus("APPLIED");
        } else {
            receipt.setStatus("PARTIAL");
        }

        BigDecimal newBalance = invoice.getBalanceInr() != null
                ? invoice.getBalanceInr().subtract(amount)
                : invoice.getGrandTotal().subtract(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) newBalance = BigDecimal.ZERO;
        invoice.setBalanceInr(newBalance);
        if (newBalance.compareTo(BigDecimal.ZERO) == 0 && "POSTED".equals(invoice.getStatus())) {
            invoice.setStatus("PAID");
        } else if (newBalance.compareTo(invoice.getGrandTotal()) < 0 && "POSTED".equals(invoice.getStatus())) {
            invoice.setStatus("PARTIALLY_PAID");
        }
        invoiceRepo.save(invoice);

        return toDto(repo.save(receipt));
    }

    private AdvanceReceiptDto toDto(AdvanceReceipt r) {
        return new AdvanceReceiptDto(r.getId(), r.getCustomerId(), r.getReceiptDate(),
                r.getAmount(), r.getCurrencyCode(), r.getReference(), r.getNotes(),
                r.getStatus(), r.getAppliedAmount(), r.getJournalId());
    }
}
