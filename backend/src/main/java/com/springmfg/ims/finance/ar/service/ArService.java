package com.springmfg.ims.finance.ar.service;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.finance.ar.domain.ArInvoice;
import com.springmfg.ims.finance.ar.domain.ArReceipt;
import com.springmfg.ims.finance.ar.dto.ArAgeingRow;
import com.springmfg.ims.finance.ar.dto.ArInvoiceDto;
import com.springmfg.ims.finance.ar.dto.ArReceiptRequest;
import com.springmfg.ims.finance.ar.repository.ArInvoiceRepository;
import com.springmfg.ims.finance.ar.repository.ArReceiptRepository;
import com.springmfg.ims.finance.gl.dto.AutoJournalRequest;
import com.springmfg.ims.finance.gl.dto.JournalLineRequest;
import com.springmfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.springmfg.ims.finance.gl.service.JournalService;
import com.springmfg.ims.partner.domain.Customer;
import com.springmfg.ims.partner.repository.CustomerRepository;
import com.springmfg.ims.sales.domain.Dispatch;
import com.springmfg.ims.sales.repository.DispatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class ArService {

    // GL account IDs seeded in V15 (1101=AR Control, 4001=Revenue, 1201=Bank)
    private static final long GL_AR_CONTROL = 1101L;
    private static final long GL_REVENUE = 4001L;
    private static final long GL_BANK = 1201L;

    private final ArInvoiceRepository arInvoiceRepo;
    private final ArReceiptRepository arReceiptRepo;
    private final DispatchRepository dispatchRepo;
    private final FiscalPeriodRepository periodRepo;
    private final JournalService journalService;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final CustomerRepository customerRepo;

    public ArService(ArInvoiceRepository arInvoiceRepo,
                     ArReceiptRepository arReceiptRepo,
                     DispatchRepository dispatchRepo,
                     FiscalPeriodRepository periodRepo,
                     JournalService journalService,
                     DocumentNumberService numberService,
                     AuditService auditService,
                     CustomerRepository customerRepo) {
        this.arInvoiceRepo = arInvoiceRepo;
        this.arReceiptRepo = arReceiptRepo;
        this.dispatchRepo = dispatchRepo;
        this.periodRepo = periodRepo;
        this.journalService = journalService;
        this.numberService = numberService;
        this.auditService = auditService;
        this.customerRepo = customerRepo;
    }

    public ArInvoiceDto autoCreateFromDispatch(Long dispatchId) {
        if (arInvoiceRepo.findByDispatchId(dispatchId).isPresent()) {
            return toDto(arInvoiceRepo.findByDispatchId(dispatchId).get());
        }

        Dispatch dispatch = dispatchRepo.findById(dispatchId)
                .orElseThrow(() -> new NotFoundException("Dispatch", dispatchId));

        LocalDate invoiceDate = dispatch.getDispatchDate();
        var period = periodRepo.findOpenPeriodForDate(invoiceDate)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.PERIOD_CLOSED,
                        "No open period for " + invoiceDate));

        BigDecimal invoiceValue = computeDispatchValue(dispatch);
        LocalDate dueDate = invoiceDate.plusDays(30);

        ArInvoice inv = new ArInvoice();
        inv.setInvoiceNumber(numberService.next("ARINV"));
        inv.setCustomerId(dispatch.getCustomerId());
        inv.setDispatchId(dispatchId);
        inv.setInvoiceDate(invoiceDate);
        inv.setDueDate(dueDate);
        inv.setSubtotal(invoiceValue);
        inv.setTaxTotal(BigDecimal.ZERO);
        inv.setTotalAmount(invoiceValue);
        inv.setReceivedAmount(BigDecimal.ZERO);
        inv.setStatus("OPEN");
        inv.setPeriodId(period.getId());
        inv = arInvoiceRepo.save(inv);

        var jnlReq = new AutoJournalRequest(
                "AR_INVOICE", "SALES", "DISPATCH", dispatchId,
                invoiceDate,
                "AR Invoice " + inv.getInvoiceNumber() + " for dispatch " + dispatch.getDispatchNumber(),
                currentUserId(),
                List.of(
                        new JournalLineRequest(GL_AR_CONTROL, invoiceValue, BigDecimal.ZERO, "AR Dr"),
                        new JournalLineRequest(GL_REVENUE, BigDecimal.ZERO, invoiceValue, "Revenue Cr")
                )
        );
        var jnl = journalService.postAutoJournal(jnlReq);
        inv.setJournalHeaderId(jnl.id());

        auditService.record("AR_INVOICE_CREATED", "AR_INVOICE", String.valueOf(inv.getId()),
                null, "OPEN", null);
        return toDto(arInvoiceRepo.save(inv));
    }

    public ArInvoiceDto recordReceipt(Long invoiceId, ArReceiptRequest req) {
        ArInvoice inv = findInvoice(invoiceId);
        if (!"OPEN".equals(inv.getStatus()) && !"PARTIAL".equals(inv.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Invoice " + inv.getInvoiceNumber() + " is not receivable");
        }
        BigDecimal balance = inv.getTotalAmount().subtract(inv.getReceivedAmount());
        if (req.amount().compareTo(balance) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Receipt amount exceeds outstanding balance of " + balance);
        }

        var jnlReq = new AutoJournalRequest(
                "AR_RECEIPT", "AR", "CUSTOMER_RECEIPT", invoiceId,
                req.receiptDate(),
                "AR Receipt for " + inv.getInvoiceNumber(),
                currentUserId(),
                List.of(
                        new JournalLineRequest(GL_BANK, req.amount(), BigDecimal.ZERO, "Receipt Dr"),
                        new JournalLineRequest(GL_AR_CONTROL, BigDecimal.ZERO, req.amount(), "Receipt Cr")
                )
        );
        var jnl = journalService.postAutoJournal(jnlReq);

        ArReceipt receipt = new ArReceipt();
        receipt.setReceiptNumber(numberService.next("ARRCT"));
        receipt.setArInvoiceId(invoiceId);
        receipt.setCustomerId(inv.getCustomerId());
        receipt.setReceiptDate(req.receiptDate());
        receipt.setAmount(req.amount());
        receipt.setPaymentMode(req.paymentMode() != null ? req.paymentMode() : "BANK_TRANSFER");
        receipt.setReferenceNumber(req.referenceNumber());
        receipt.setJournalHeaderId(jnl.id());
        arReceiptRepo.save(receipt);

        BigDecimal newReceived = inv.getReceivedAmount().add(req.amount());
        inv.setReceivedAmount(newReceived);
        inv.setStatus(newReceived.compareTo(inv.getTotalAmount()) >= 0 ? "PAID" : "PARTIAL");
        return toDto(arInvoiceRepo.save(inv));
    }

    @Transactional(readOnly = true)
    public Page<ArInvoiceDto> findAll(Pageable pageable) {
        return arInvoiceRepo.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<ArAgeingRow> getAgeing() {
        List<ArInvoice> openInvoices = arInvoiceRepo.findByStatusIn(List.of("OPEN", "PARTIAL"));
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
                BigDecimal bal = inv.getTotalAmount().subtract(inv.getReceivedAmount());
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

    private BigDecimal computeDispatchValue(Dispatch dispatch) {
        // Use invoice reference as value hint; default to zero if unavailable
        // Actual value comes from dispatch items in a full implementation
        return BigDecimal.ZERO;
    }

    private ArInvoice findInvoice(Long id) {
        return arInvoiceRepo.findById(id).orElseThrow(() -> new NotFoundException("ArInvoice", id));
    }

    private ArInvoiceDto toDto(ArInvoice inv) {
        BigDecimal balance = inv.getTotalAmount().subtract(inv.getReceivedAmount());
        return new ArInvoiceDto(inv.getId(), inv.getInvoiceNumber(), inv.getCustomerId(),
                inv.getDispatchId(), inv.getInvoiceDate(), inv.getDueDate(),
                inv.getSubtotal(), inv.getTaxTotal(), inv.getTotalAmount(),
                inv.getReceivedAmount(), balance, inv.getStatus(),
                inv.getPeriodId(), inv.getJournalHeaderId());
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.springmfg.ims.iam.domain.User user) {
                return user.getId();
            }
        } catch (Exception ignored) {}
        return -1L;
    }
}
