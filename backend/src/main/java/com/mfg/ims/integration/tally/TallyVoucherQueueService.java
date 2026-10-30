package com.mfg.ims.integration.tally;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.enterprise.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Transactional
public class TallyVoucherQueueService {

    private final TallyVoucherQueueRepository repo;
    private final CompanyRepository companyRepo;

    public TallyVoucherQueueService(TallyVoucherQueueRepository repo, CompanyRepository companyRepo) {
        this.repo = repo;
        this.companyRepo = companyRepo;
    }

    /** Returns the primary company's GSTIN, or null if not configured. */
    @Transactional(readOnly = true)
    public String companyGstin() {
        return companyRepo.findById(1L).map(c -> c.getGstin()).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<TallyVoucherQueueDto> list(String status) {
        List<TallyVoucherQueue> items = (status != null && !status.isBlank())
                ? repo.findByCompanyIdAndStatusOrderByCreatedAtDesc(1L, status)
                : repo.findByCompanyIdOrderByCreatedAtDesc(1L);
        return items.stream().map(TallyVoucherQueueDto::from).toList();
    }

    public TallyVoucherQueueDto approve(Long id, Long actorId) {
        TallyVoucherQueue q = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("TallyVoucherQueue", id));
        q.setStatus("APPROVED");
        q.setApprovedBy(actorId);
        q.setApprovedAt(OffsetDateTime.now());
        q.setUpdatedAt(OffsetDateTime.now());
        q.setUpdatedBy(actorId);
        return TallyVoucherQueueDto.from(repo.save(q));
    }

    public TallyVoucherQueueDto reject(Long id, Long actorId) {
        TallyVoucherQueue q = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("TallyVoucherQueue", id));
        q.setStatus("REJECTED");
        q.setUpdatedAt(OffsetDateTime.now());
        q.setUpdatedBy(actorId);
        return TallyVoucherQueueDto.from(repo.save(q));
    }

    public TallyVoucherQueueDto markSent(Long id, Long actorId) {
        TallyVoucherQueue q = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("TallyVoucherQueue", id));
        q.setStatus("SENT");
        q.setSentAt(OffsetDateTime.now());
        q.setUpdatedAt(OffsetDateTime.now());
        q.setUpdatedBy(actorId);
        return TallyVoucherQueueDto.from(repo.save(q));
    }

    public int countByStatus(String status) {
        return repo.findByCompanyIdAndStatusOrderByCreatedAtDesc(1L, status).size();
    }

    public TallyVoucherQueueDto enqueue(String sourceType, Long sourceId, String voucherType,
                                         String payloadXml, Long actorId) {
        TallyVoucherQueue q = new TallyVoucherQueue();
        q.setSourceType(sourceType);
        q.setSourceId(sourceId);
        q.setVoucherType(voucherType);
        q.setPayloadXml(payloadXml);
        q.setCreatedBy(actorId);
        return TallyVoucherQueueDto.from(repo.save(q));
    }

    public TallyVoucherQueueDto markLedgerMissing(Long id, String accountCode, Long actorId) {
        TallyVoucherQueue q = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("TallyVoucherQueue", id));
        q.setStatus("LEDGER_MISSING");
        q.setExceptionCode("LEDGER_MISSING");
        q.setExceptionDetail("Ledger not mapped: " + accountCode);
        q.setUpdatedAt(OffsetDateTime.now());
        q.setUpdatedBy(actorId);
        return TallyVoucherQueueDto.from(repo.save(q));
    }

    // ── Day-book ─────────────────────────────────────────────────────────────

    public record DayBookEntryDto(Long id, String voucherType, String documentRef, OffsetDateTime sentAt) {}

    @Transactional(readOnly = true)
    public List<DayBookEntryDto> dayBook(LocalDate from, LocalDate to) {
        OffsetDateTime fromDt = from != null ? from.atStartOfDay().atOffset(ZoneOffset.UTC) :
                LocalDate.now().withDayOfMonth(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt = to != null ? to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC) :
                LocalDate.now().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return repo.findByCompanyIdAndStatusAndSentAtBetweenOrderBySentAtDesc(1L, "SENT", fromDt, toDt)
                .stream()
                .map(q -> new DayBookEntryDto(q.getId(), q.getVoucherType(),
                        q.getSourceType() != null ? q.getSourceType() + "/" + q.getSourceId() : null,
                        q.getSentAt()))
                .toList();
    }

    // ── Reconciliation ───────────────────────────────────────────────────────

    public record ReconciliationStatusDto(long sent, long reconciled, long unreconciled) {}

    @Transactional(readOnly = true)
    public ReconciliationStatusDto reconciliationStatus(LocalDate from, LocalDate to) {
        OffsetDateTime fromDt = from != null ? from.atStartOfDay().atOffset(ZoneOffset.UTC) :
                LocalDate.now().withDayOfMonth(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt = to != null ? to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC) :
                LocalDate.now().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        List<TallyVoucherQueue> sent =
                repo.findByCompanyIdAndStatusAndSentAtBetweenOrderBySentAtDesc(1L, "SENT", fromDt, toDt);
        long reconciledCount = sent.stream().filter(q -> q.getReconciledAt() != null).count();
        return new ReconciliationStatusDto(sent.size(), reconciledCount, sent.size() - reconciledCount);
    }

    public void reconcile(List<Long> ids) {
        OffsetDateTime now = OffsetDateTime.now();
        for (Long id : ids) {
            repo.findById(id).ifPresent(q -> {
                q.setReconciledAt(now);
                q.setUpdatedAt(now);
                repo.save(q);
            });
        }
    }

    // ── Counts for flow section ───────────────────────────────────────────────

    public record VoucherQueueCountsDto(long draft, long approved, long sent, long ledgerMissing, long failed) {}

    @Transactional(readOnly = true)
    public VoucherQueueCountsDto counts() {
        long draft = repo.countByCompanyIdAndStatus(1L, "PENDING_APPROVAL");
        long approved = repo.countByCompanyIdAndStatus(1L, "APPROVED");
        long sent = repo.countByCompanyIdAndStatus(1L, "SENT");
        long ledgerMissing = repo.countByCompanyIdAndStatus(1L, "LEDGER_MISSING");
        long failed = repo.countByCompanyIdAndStatus(1L, "EXCEPTION");
        return new VoucherQueueCountsDto(draft, approved, sent, ledgerMissing, failed);
    }
}
