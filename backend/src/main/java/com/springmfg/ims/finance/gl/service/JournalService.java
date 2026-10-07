package com.springmfg.ims.finance.gl.service;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.finance.gl.domain.FiscalPeriod;
import com.springmfg.ims.finance.gl.domain.GlAccount;
import com.springmfg.ims.finance.gl.domain.JournalHeader;
import com.springmfg.ims.finance.gl.domain.JournalLine;
import com.springmfg.ims.finance.gl.dto.*;
import com.springmfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.springmfg.ims.finance.gl.repository.GlAccountRepository;
import com.springmfg.ims.finance.gl.repository.JournalHeaderRepository;
import com.springmfg.ims.finance.gl.repository.JournalLineRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class JournalService {

    private final JournalHeaderRepository journalHeaderRepo;
    private final JournalLineRepository journalLineRepo;
    private final FiscalPeriodRepository periodRepo;
    private final GlAccountRepository glAccountRepo;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    public JournalService(JournalHeaderRepository journalHeaderRepo,
                          JournalLineRepository journalLineRepo,
                          FiscalPeriodRepository periodRepo,
                          GlAccountRepository glAccountRepo,
                          DocumentNumberService numberService,
                          AuditService auditService) {
        this.journalHeaderRepo = journalHeaderRepo;
        this.journalLineRepo = journalLineRepo;
        this.periodRepo = periodRepo;
        this.glAccountRepo = glAccountRepo;
        this.numberService = numberService;
        this.auditService = auditService;
    }

    public JournalHeaderDto createDraft(JournalCreateRequest req) {
        List<JournalLineRequest> requestLines = normaliseLines(req.lines(), false);
        validateBalance(requestLines);
        FiscalPeriod period = findOpenPeriod(req.postingDate());

        JournalHeader header = new JournalHeader();
        header.setJournalNumber(numberService.next("JNL"));
        header.setJournalType(req.journalType());
        header.setPostingDate(req.postingDate());
        header.setPeriodId(period.getId());
        header.setDescription(req.description());
        header.setCurrencyCode(req.currencyCode() != null ? req.currencyCode() : "INR");
        header.setStatus("DRAFT");
        header.setPreparedBy(currentUserId());
        header = journalHeaderRepo.save(header);

        List<JournalLine> lines = buildLines(header, requestLines);
        header.setLines(lines);

        return toDto(header);
    }

    /** Called from an AFTER_COMMIT listener: the originating transaction is already committed, so this must start its own. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JournalHeaderDto postAutoJournal(AutoJournalRequest req) {
        return createPostedJournal(req);
    }

    /**
     * Posts a balanced journal inside the caller's transaction, so the journal and the business document that
     * caused it (for example a payroll run) commit or roll back together. Fiscal-period gating and Dr = Cr apply.
     */
    public JournalHeaderDto postSourceJournal(AutoJournalRequest req) {
        return createPostedJournal(req);
    }

    private JournalHeaderDto createPostedJournal(AutoJournalRequest req) {
        FiscalPeriod period = findOpenPeriod(req.postingDate());
        List<JournalLineRequest> requestLines = normaliseLines(req.lines(), true);
        validateBalance(requestLines);

        JournalHeader header = new JournalHeader();
        header.setJournalNumber(numberService.next("JNL"));
        header.setJournalType(req.journalType());
        header.setSourceModule(req.sourceModule());
        header.setSourceDocumentType(req.sourceDocumentType());
        header.setSourceDocumentId(req.sourceDocumentId());
        header.setPostingDate(req.postingDate());
        header.setPeriodId(period.getId());
        header.setDescription(req.description());
        header.setCurrencyCode("INR");
        header.setStatus("POSTED");
        header.setPreparedBy(req.preparedBy() != null ? req.preparedBy() : -1L);
        header.setApprovedBy(req.preparedBy());
        header.setApprovedAt(Instant.now());
        header.setPostedBy(req.preparedBy());
        header.setPostedAt(Instant.now());
        header = journalHeaderRepo.save(header);

        header.setLines(buildLines(header, requestLines));
        return toDto(header);
    }

    public JournalHeaderDto submit(Long id) {
        JournalHeader header = findHeader(id);
        requireStatus(header, "DRAFT");
        header.setStatus("SUBMITTED");
        return toDto(journalHeaderRepo.save(header));
    }

    public JournalHeaderDto approve(Long id) {
        JournalHeader header = findHeader(id);
        requireStatus(header, "SUBMITTED");
        header.setStatus("APPROVED");
        header.setApprovedBy(currentUserId());
        header.setApprovedAt(Instant.now());
        auditService.record("JOURNAL_APPROVED", "JOURNAL", String.valueOf(id),
                "SUBMITTED", "APPROVED", null);
        return toDto(journalHeaderRepo.save(header));
    }

    public JournalHeaderDto post(Long id) {
        JournalHeader header = findHeader(id);
        requireStatus(header, "APPROVED");
        FiscalPeriod period = periodRepo.findById(header.getPeriodId())
                .orElseThrow(() -> new NotFoundException("FiscalPeriod", header.getPeriodId()));
        if (!"OPEN".equals(period.getStatus())) {
            throw new BusinessRuleException(ErrorCode.PERIOD_CLOSED,
                    "Period " + period.getPeriodName() + " is not OPEN");
        }
        header.setStatus("POSTED");
        header.setPostedBy(currentUserId());
        header.setPostedAt(Instant.now());
        auditService.record("JOURNAL_POSTED", "JOURNAL", String.valueOf(id),
                "APPROVED", "POSTED", null);
        return toDto(journalHeaderRepo.save(header));
    }

    public JournalHeaderDto reverse(Long id, String reason) {
        JournalHeader original = findHeader(id);
        requireStatus(original, "POSTED");

        FiscalPeriod period = findOpenPeriod(LocalDate.now());

        JournalHeader reversal = new JournalHeader();
        reversal.setJournalNumber(numberService.next("JNL"));
        reversal.setJournalType("REVERSAL");
        reversal.setPostingDate(LocalDate.now());
        reversal.setPeriodId(period.getId());
        reversal.setDescription("Reversal of " + original.getJournalNumber() + ": " + reason);
        reversal.setCurrencyCode(original.getCurrencyCode());
        reversal.setStatus("POSTED");
        reversal.setReversalOfId(original.getId());
        reversal.setPreparedBy(currentUserId());
        reversal.setApprovedBy(currentUserId());
        reversal.setApprovedAt(Instant.now());
        reversal.setPostedBy(currentUserId());
        reversal.setPostedAt(Instant.now());
        reversal = journalHeaderRepo.save(reversal);

        List<JournalLineRequest> reversedLines = original.getLines().stream()
                .map(l -> new JournalLineRequest(l.getGlAccountId(),
                        l.getCreditAmount(), l.getDebitAmount(), "Reversal: " + l.getDescription(),
                        l.getCostCentreId(), l.getProfitCentreId()))
                .toList();
        buildLines(reversal, reversedLines);

        // The original stays POSTED forever otherwise, and could be reversed again and again.
        original.setStatus("REVERSED");
        journalHeaderRepo.save(original);

        auditService.record("JOURNAL_REVERSED", "JOURNAL", String.valueOf(id),
                "POSTED", "REVERSED", reason);
        return toDto(journalHeaderRepo.findById(reversal.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public JournalHeaderDto findById(Long id) {
        return toDto(findHeader(id));
    }

    @Transactional(readOnly = true)
    public Page<JournalHeaderDto> findAll(Pageable pageable) {
        return journalHeaderRepo.findAll(pageable).map(this::toDtoNoLines);
    }

    @Transactional(readOnly = true)
    public List<TrialBalanceRow> getTrialBalance(Long periodId) {
        List<Object[]> rows = journalLineRepo.trialBalanceByPeriod(periodId);
        Map<Long, GlAccount> accounts = glAccountRepo.findAll().stream()
                .collect(Collectors.toMap(GlAccount::getId, Function.identity()));

        return rows.stream().map(r -> {
            Long accountId = (Long) r[0];
            BigDecimal dr = (BigDecimal) r[1];
            BigDecimal cr = (BigDecimal) r[2];
            GlAccount acc = accounts.get(accountId);
            BigDecimal balance = dr.subtract(cr);
            return new TrialBalanceRow(accountId,
                    acc != null ? acc.getAccountCode() : "",
                    acc != null ? acc.getName() : "",
                    acc != null ? acc.getAccountType() : "",
                    dr, cr, balance);
        }).toList();
    }

    /**
     * A journal line carries exactly one of debit or credit, and a journal needs at least two such lines. Machine-built
     * journals drop lines whose amount is zero (a zero-priced item has nothing to account for); a hand-entered journal
     * with such a line is a mistake the user must see, so it is rejected rather than silently changed.
     */
    private List<JournalLineRequest> normaliseLines(List<JournalLineRequest> lines, boolean dropZeroLines) {
        List<JournalLineRequest> kept = new ArrayList<>();
        for (JournalLineRequest l : lines) {
            BigDecimal dr = l.debitAmount() != null ? l.debitAmount() : BigDecimal.ZERO;
            BigDecimal cr = l.creditAmount() != null ? l.creditAmount() : BigDecimal.ZERO;
            if (dr.signum() > 0 && cr.signum() > 0) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "A journal line carries either a debit or a credit, not both");
            }
            if (dr.signum() == 0 && cr.signum() == 0) {
                if (dropZeroLines) {
                    continue;
                }
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "A journal line must carry a debit or a credit amount");
            }
            kept.add(l);
        }
        if (kept.size() < 2) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A journal needs at least two lines with a non-zero amount");
        }
        return kept;
    }

    private void validateBalance(List<JournalLineRequest> lines) {
        BigDecimal totalDr = lines.stream().map(l -> l.debitAmount() != null ? l.debitAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCr = lines.stream().map(l -> l.creditAmount() != null ? l.creditAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDr.compareTo(totalCr) != 0) {
            throw new BusinessRuleException(ErrorCode.JOURNAL_NOT_BALANCED,
                    String.format("Debit %.4f != Credit %.4f", totalDr, totalCr));
        }
    }

    private FiscalPeriod findOpenPeriod(LocalDate date) {
        return periodRepo.findOpenPeriodForDate(date)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.PERIOD_CLOSED,
                        "No open fiscal period for date " + date));
    }

    private List<JournalLine> buildLines(JournalHeader header, List<JournalLineRequest> requests) {
        List<JournalLine> lines = new ArrayList<>();
        int seq = 1;
        for (JournalLineRequest lr : requests) {
            JournalLine line = new JournalLine();
            line.setJournalHeader(header);
            line.setLineNumber(seq++);
            line.setGlAccountId(lr.glAccountId());
            line.setDebitAmount(lr.debitAmount() != null ? lr.debitAmount() : BigDecimal.ZERO);
            line.setCreditAmount(lr.creditAmount() != null ? lr.creditAmount() : BigDecimal.ZERO);
            line.setDescription(lr.description());
            line.setCostCentreId(lr.costCentreId());
            line.setProfitCentreId(lr.profitCentreId());
            lines.add(journalLineRepo.save(line));
        }
        return lines;
    }

    private JournalHeader findHeader(Long id) {
        return journalHeaderRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("JournalHeader", id));
    }

    private void requireStatus(JournalHeader header, String expected) {
        if (!expected.equals(header.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Journal " + header.getJournalNumber() + " is " + header.getStatus() +
                    ", expected " + expected);
        }
    }

    private JournalHeaderDto toDto(JournalHeader h) {
        Map<Long, GlAccount> accounts = glAccountRepo.findAll().stream()
                .collect(Collectors.toMap(GlAccount::getId, Function.identity()));
        List<JournalLineDto> lineDtos = h.getLines().stream().map(l -> {
            GlAccount acc = accounts.get(l.getGlAccountId());
            return new JournalLineDto(l.getId(), l.getLineNumber(), l.getGlAccountId(),
                    acc != null ? acc.getAccountCode() : "",
                    acc != null ? acc.getName() : "",
                    l.getDebitAmount(), l.getCreditAmount(), l.getDescription(),
                    l.getCostCentreId(), l.getProfitCentreId());
        }).toList();
        return new JournalHeaderDto(h.getId(), h.getJournalNumber(), h.getJournalType(),
                h.getSourceModule(), h.getSourceDocumentType(), h.getSourceDocumentId(),
                h.getPostingDate(), h.getPeriodId(), h.getDescription(),
                h.getCurrencyCode(), h.getExchangeRate(), h.getStatus(),
                h.getReversalOfId(), h.getPreparedBy(), h.getApprovedBy(), h.getApprovedAt(),
                h.getPostedBy(), h.getPostedAt(), lineDtos);
    }

    private JournalHeaderDto toDtoNoLines(JournalHeader h) {
        return new JournalHeaderDto(h.getId(), h.getJournalNumber(), h.getJournalType(),
                h.getSourceModule(), h.getSourceDocumentType(), h.getSourceDocumentId(),
                h.getPostingDate(), h.getPeriodId(), h.getDescription(),
                h.getCurrencyCode(), h.getExchangeRate(), h.getStatus(),
                h.getReversalOfId(), h.getPreparedBy(), h.getApprovedBy(), h.getApprovedAt(),
                h.getPostedBy(), h.getPostedAt(), List.of());
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
