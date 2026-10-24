package com.mfg.ims.controlling.service;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.controlling.domain.CostAllocationRule;
import com.mfg.ims.controlling.domain.CostAllocationRun;
import com.mfg.ims.controlling.dto.*;
import com.mfg.ims.controlling.repository.CostAllocationRuleRepository;
import com.mfg.ims.controlling.repository.CostAllocationRunRepository;
import com.mfg.ims.finance.gl.domain.FiscalPeriod;
import com.mfg.ims.finance.gl.dto.AutoJournalRequest;
import com.mfg.ims.finance.gl.dto.JournalHeaderDto;
import com.mfg.ims.finance.gl.dto.JournalLineRequest;
import com.mfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.mfg.ims.finance.gl.repository.GlAccountRepository;
import com.mfg.ims.finance.gl.service.JournalService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CostAllocationService {

    private final CostAllocationRuleRepository ruleRepo;
    private final CostAllocationRunRepository runRepo;
    private final FiscalPeriodRepository periodRepo;
    private final GlAccountRepository glAccountRepo;
    private final JournalService journalService;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;

    public CostAllocationService(CostAllocationRuleRepository ruleRepo,
                                  CostAllocationRunRepository runRepo,
                                  FiscalPeriodRepository periodRepo,
                                  GlAccountRepository glAccountRepo,
                                  JournalService journalService,
                                  AuditService auditService,
                                  JdbcTemplate jdbc) {
        this.ruleRepo = ruleRepo;
        this.runRepo = runRepo;
        this.periodRepo = periodRepo;
        this.glAccountRepo = glAccountRepo;
        this.journalService = journalService;
        this.auditService = auditService;
        this.jdbc = jdbc;
    }

    // ── Rules ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CostAllocationRuleDto> findAllRules() {
        return ruleRepo.findAllWithCostCentre().stream().map(row -> new CostAllocationRuleDto(
                toLong(row[0]), toLong(row[1]), (String) row[2], (String) row[3],
                (String) row[4], (Boolean) row[5])).toList();
    }

    @Transactional
    public CostAllocationRuleDto createRule(SaveAllocationRuleRequest req) {
        CostAllocationRule rule = new CostAllocationRule();
        rule.setCostCentreId(req.costCentreId());
        rule.setAllocationBase(req.allocationBase());
        rule = ruleRepo.save(rule);
        auditService.record("CREATED", "COST_ALLOCATION_RULE", String.valueOf(rule.getId()),
                null, req.costCentreId() + "/" + req.allocationBase(), null);
        return findAllRules().stream().filter(r -> r.id().equals(rule.getId())).findFirst()
                .orElseThrow();
    }

    @Transactional
    public CostAllocationRuleDto setRuleActive(Long id, boolean active) {
        CostAllocationRule rule = ruleRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("CostAllocationRule", id));
        rule.setActive(active);
        ruleRepo.save(rule);
        auditService.record(active ? "ACTIVATED" : "DEACTIVATED", "COST_ALLOCATION_RULE",
                String.valueOf(id), String.valueOf(!active), String.valueOf(active), null);
        return findAllRules().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    // ── Runs ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AllocationRunDto> findRuns() {
        return runRepo.findAllByOrderByRunAtDesc().stream()
                .map(r -> toDto(r, List.of())).toList();
    }

    @Transactional(readOnly = true)
    public AllocationRunDto findRunById(Long id) {
        CostAllocationRun run = runRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("CostAllocationRun", id));
        List<AllocationLineDto> lines = runRepo.findLinesForRun(id).stream()
                .map(row -> new AllocationLineDto(
                        toLong(row[0]), toLong(row[1]), (String) row[2],
                        toLong(row[3]), (String) row[4], (String) row[5],
                        (String) row[6], toBd(row[7]), toBd(row[8]), toBd(row[9]))).toList();
        return toDto(run, lines);
    }

    /**
     * Execute the allocation cycle for a period.
     * For each active rule: sum overhead expense for the cost centre from GL lines,
     * sum the allocation base from production operations, allocate proportionally,
     * post a balanced journal (Dr 5304 per product / Cr 5305 total).
     */
    @Transactional
    public AllocationRunDto runAllocation(Long periodId, Long userId) {
        FiscalPeriod period = periodRepo.findById(periodId)
                .orElseThrow(() -> new NotFoundException("FiscalPeriod", periodId));

        Long acct5304 = glAccountRepo.findByAccountCode("5304")
                .orElseThrow(() -> new NotFoundException("GL account 5304")).getId();
        Long acct5305 = glAccountRepo.findByAccountCode("5305")
                .orElseThrow(() -> new NotFoundException("GL account 5305")).getId();

        List<CostAllocationRule> rules = ruleRepo.findByActiveTrue();
        if (rules.isEmpty()) {
            CostAllocationRun emptyRun = new CostAllocationRun();
            emptyRun.setPeriodId(periodId);
            emptyRun.setRunBy(userId);
            emptyRun.setStatus("COMPLETED");
            return toDto(runRepo.save(emptyRun), List.of());
        }

        // Collect allocation lines: (costCentreId, productId) -> amount
        List<Object[]> pendingLines = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (CostAllocationRule rule : rules) {
            BigDecimal overhead = ruleRepo.costCentreOverhead(rule.getCostCentreId(), periodId);
            if (overhead == null || overhead.compareTo(BigDecimal.ZERO) <= 0) continue;

            List<Object[]> baseRows = ruleRepo.productBaseQty(periodId, rule.getAllocationBase());
            BigDecimal totalBase = baseRows.stream()
                    .map(r -> toBd(r[1])).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalBase.compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal ratePerUnit = overhead.divide(totalBase, 4, RoundingMode.HALF_UP);

            for (Object[] row : baseRows) {
                Long productId = toLong(row[0]);
                BigDecimal baseQty = toBd(row[1]);
                BigDecimal amount = ratePerUnit.multiply(baseQty).setScale(4, RoundingMode.HALF_UP);
                pendingLines.add(new Object[]{rule.getCostCentreId(), productId,
                        rule.getAllocationBase(), baseQty, ratePerUnit, amount});
                grandTotal = grandTotal.add(amount);
            }
        }

        // Build journal
        List<JournalLineRequest> journalLines = new ArrayList<>();
        for (Object[] pl : pendingLines) {
            BigDecimal amount = toBd(pl[5]);
            Long costCentreId = toLong(pl[0]);
            journalLines.add(new JournalLineRequest(acct5304, amount, BigDecimal.ZERO,
                    "Overhead allocation - product " + pl[1], costCentreId, null));
        }
        if (!journalLines.isEmpty()) {
            // One credit line for the pool
            journalLines.add(new JournalLineRequest(acct5305, BigDecimal.ZERO, grandTotal,
                    "Overhead pool released", null, null));
        }

        Long journalHeaderId = null;
        if (!journalLines.isEmpty()) {
            JournalHeaderDto jh = journalService.postAutoJournal(new AutoJournalRequest(
                    "OVERHEAD_ALLOCATION", "CONTROLLING", "COST_ALLOCATION_RUN",
                    null, period.getEndDate(), "Period-end overhead allocation - period "
                    + period.getFiscalYear() + "/" + period.getPeriodNumber(),
                    userId, journalLines));
            journalHeaderId = jh.id();
        }

        // Persist the run
        CostAllocationRun run = new CostAllocationRun();
        run.setPeriodId(periodId);
        run.setRunBy(userId);
        run.setRunAt(Instant.now());
        run.setStatus("COMPLETED");
        run.setJournalHeaderId(journalHeaderId);
        run.setTotalAllocated(grandTotal);
        run = runRepo.save(run);

        // Persist allocation lines
        if (!pendingLines.isEmpty()) {
            final Long runId = run.getId();
            jdbc.batchUpdate(
                    "INSERT INTO ims.cost_allocation_lines " +
                    "(run_id, cost_centre_id, product_id, allocation_base, base_qty, rate_per_unit, allocated_amount) " +
                    "VALUES (?,?,?,?,?,?,?)",
                    pendingLines.stream().map(pl -> new Object[]{
                            runId, pl[0], pl[1], pl[2], pl[3], pl[4], pl[5]
                    }).toList());
        }

        auditService.record("ALLOCATION_RUN", "COST_ALLOCATION_RUN", String.valueOf(run.getId()),
                null, "period=" + periodId + " total=" + grandTotal, null);

        return findRunById(run.getId());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AllocationRunDto toDto(CostAllocationRun r, List<AllocationLineDto> lines) {
        FiscalPeriod period = periodRepo.findById(r.getPeriodId()).orElse(null);
        String periodName = period != null
                ? period.getFiscalYear() + "/" + period.getPeriodNumber() : "";
        return new AllocationRunDto(r.getId(), r.getPeriodId(), periodName,
                r.getStatus(), r.getRunAt(), r.getRunBy(),
                r.getJournalHeaderId(), r.getTotalAllocated(), lines);
    }

    private static Long toLong(Object v) {
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        return null;
    }

    private static BigDecimal toBd(Object v) {
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
