package com.springmfg.ims.finance.ap.service;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.finance.ap.domain.ApInvoice;
import com.springmfg.ims.finance.ap.domain.ApPayment;
import com.springmfg.ims.finance.ap.dto.ApAgeingRow;
import com.springmfg.ims.finance.ap.dto.ApInvoiceCreateRequest;
import com.springmfg.ims.finance.ap.dto.ApInvoiceDto;
import com.springmfg.ims.finance.ap.dto.ApPaymentRequest;
import com.springmfg.ims.finance.ap.repository.ApInvoiceRepository;
import com.springmfg.ims.finance.ap.repository.ApPaymentRepository;
import com.springmfg.ims.finance.gl.dto.AutoJournalRequest;
import com.springmfg.ims.finance.gl.dto.JournalLineRequest;
import com.springmfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.springmfg.ims.finance.gl.service.JournalService;
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

    // GL account IDs seeded in V16 (2401=AP Control, 2402=AP Payable, 1201=Bank)
    private static final long GL_AP_CONTROL = 2401L;
    private static final long GL_AP_PAYABLE = 2402L;
    private static final long GL_BANK = 1201L;

    private final ApInvoiceRepository apInvoiceRepo;
    private final ApPaymentRepository apPaymentRepo;
    private final FiscalPeriodRepository periodRepo;
    private final JournalService journalService;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final SupplierRepository supplierRepo;

    public ApService(ApInvoiceRepository apInvoiceRepo,
                     ApPaymentRepository apPaymentRepo,
                     FiscalPeriodRepository periodRepo,
                     JournalService journalService,
                     DocumentNumberService numberService,
                     AuditService auditService,
                     SupplierRepository supplierRepo) {
        this.apInvoiceRepo = apInvoiceRepo;
        this.apPaymentRepo = apPaymentRepo;
        this.periodRepo = periodRepo;
        this.journalService = journalService;
        this.numberService = numberService;
        this.auditService = auditService;
        this.supplierRepo = supplierRepo;
    }

    public ApInvoiceDto createInvoice(ApInvoiceCreateRequest req) {
        var period = periodRepo.findOpenPeriodForDate(req.invoiceDate())
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.PERIOD_CLOSED,
                        "No open period for " + req.invoiceDate()));

        BigDecimal tax = req.taxTotal() != null ? req.taxTotal() : BigDecimal.ZERO;
        BigDecimal total = req.subtotal().add(tax);

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
        inv = apInvoiceRepo.save(inv);

        auditService.record("AP_INVOICE_CREATED", "AP_INVOICE", String.valueOf(inv.getId()),
                null, "DRAFT", null);
        return toDto(inv);
    }

    public ApInvoiceDto openInvoice(Long id) {
        ApInvoice inv = findInvoice(id);
        if (!"DRAFT".equals(inv.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Invoice " + inv.getInvoiceNumber() + " is not DRAFT");
        }

        var jnlReq = new AutoJournalRequest(
                "AP_INVOICE", "AP", "SUPPLIER_INVOICE", inv.getId(),
                inv.getInvoiceDate(),
                "AP Invoice " + inv.getInvoiceNumber(),
                currentUserId(),
                List.of(
                        new JournalLineRequest(GL_AP_CONTROL, inv.getTotalAmount(), BigDecimal.ZERO,
                                "AP Invoice Dr"),
                        new JournalLineRequest(GL_AP_PAYABLE, BigDecimal.ZERO, inv.getTotalAmount(),
                                "AP Invoice Cr")
                )
        );
        var jnl = journalService.postAutoJournal(jnlReq);

        inv.setStatus("OPEN");
        inv.setJournalHeaderId(jnl.id());
        auditService.record("AP_INVOICE_OPENED", "AP_INVOICE", String.valueOf(id),
                "DRAFT", "OPEN", null);
        return toDto(apInvoiceRepo.save(inv));
    }

    public ApInvoiceDto recordPayment(Long invoiceId, ApPaymentRequest req) {
        ApInvoice inv = findInvoice(invoiceId);
        if (!"OPEN".equals(inv.getStatus()) && !"PARTIAL".equals(inv.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Invoice " + inv.getInvoiceNumber() + " is not payable");
        }
        BigDecimal balance = inv.getTotalAmount().subtract(inv.getPaidAmount());
        if (req.amount().compareTo(balance) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Payment amount exceeds outstanding balance of " + balance);
        }

        var jnlReq = new AutoJournalRequest(
                "AP_PAYMENT", "AP", "SUPPLIER_PAYMENT", invoiceId,
                req.paymentDate(),
                "AP Payment for " + inv.getInvoiceNumber(),
                currentUserId(),
                List.of(
                        new JournalLineRequest(GL_AP_PAYABLE, req.amount(), BigDecimal.ZERO, "Payment Dr"),
                        new JournalLineRequest(GL_BANK, BigDecimal.ZERO, req.amount(), "Payment Cr")
                )
        );
        var jnl = journalService.postAutoJournal(jnlReq);

        ApPayment payment = new ApPayment();
        payment.setPaymentNumber(numberService.next("APPMT"));
        payment.setApInvoiceId(invoiceId);
        payment.setPaymentDate(req.paymentDate());
        payment.setAmount(req.amount());
        payment.setPaymentMode(req.paymentMode() != null ? req.paymentMode() : "BANK_TRANSFER");
        payment.setReferenceNumber(req.referenceNumber());
        payment.setJournalHeaderId(jnl.id());
        apPaymentRepo.save(payment);

        BigDecimal newPaid = inv.getPaidAmount().add(req.amount());
        inv.setPaidAmount(newPaid);
        inv.setStatus(newPaid.compareTo(inv.getTotalAmount()) >= 0 ? "PAID" : "PARTIAL");
        return toDto(apInvoiceRepo.save(inv));
    }

    @Transactional(readOnly = true)
    public Page<ApInvoiceDto> findAll(Pageable pageable) {
        return apInvoiceRepo.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<ApAgeingRow> getAgeing() {
        List<ApInvoice> openInvoices = apInvoiceRepo.findByStatusIn(List.of("OPEN", "PARTIAL"));
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
                BigDecimal bal = inv.getTotalAmount().subtract(inv.getPaidAmount());
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
                inv.getPeriodId(), inv.getJournalHeaderId());
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
}
