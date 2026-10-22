package com.mfg.ims.mrp.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.ConflictException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.mrp.domain.MrpRun;
import com.mfg.ims.mrp.dto.MrpRunDto;
import com.mfg.ims.mrp.dto.RunMrpRequest;
import com.mfg.ims.mrp.engine.MrpEngine;
import com.mfg.ims.mrp.engine.MrpModel.MrpInput;
import com.mfg.ims.mrp.engine.MrpModel.MrpResult;
import com.mfg.ims.mrp.engine.MrpModel.ProposalDraft;
import com.mfg.ims.mrp.repository.MrpRunRepository;
import com.mfg.ims.mrp.repository.MrpSourceDataRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Batch MRP run (ADR 18). Planning is advisory: it only writes mrp_runs/mrp_proposals and never creates
 * requisitions, orders or stock movements (business rule 20).
 */
@Service
public class MrpRunService {

    private static final Logger log = LoggerFactory.getLogger(MrpRunService.class);

    private final MrpRunRepository runRepo;
    private final MrpSourceDataRepository sourceData;
    private final SystemSettingService settings;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final MrpRunFailureRecorder failureRecorder;

    @PersistenceContext
    private EntityManager entityManager;

    public MrpRunService(MrpRunRepository runRepo, MrpSourceDataRepository sourceData, SystemSettingService settings,
                         DocumentNumberService numberService, AuditService auditService,
                         MrpRunFailureRecorder failureRecorder) {
        this.runRepo = runRepo;
        this.sourceData = sourceData;
        this.settings = settings;
        this.numberService = numberService;
        this.auditService = auditService;
        this.failureRecorder = failureRecorder;
    }

    @Transactional
    public MrpRunDto runMrp(RunMrpRequest req) {
        int horizon = req != null && req.horizonDays() != null
                ? req.horizonDays() : settings.getInt("mrp.default_horizon_days", 90);
        boolean safety = req == null || req.includeSafetyStock() == null || req.includeSafetyStock();
        Long userId = CurrentUser.id();

        if (!sourceData.tryLockRun()) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "An MRP run is already in progress");
        }
        entityManager.flush();

        MrpRun run = new MrpRun();
        run.setRunNumber(numberService.next("MRP"));
        run.setPlanningHorizonDays(horizon);
        run.setIncludeSafetyStock(safety);
        run.setInitiatedBy(userId);
        run.setStatus("RUNNING");
        run = runRepo.save(run);

        try {
            long t0 = System.nanoTime();
            LocalDate today = LocalDate.now();
            MrpInput input = sourceData.load(today, today.plusDays(horizon), safety,
                    settings.getInt("mrp.default_purchase_lead_days", 7),
                    settings.getInt("mrp.default_production_lead_days", 3),
                    BigDecimal.valueOf(settings.getInt("mrp.machine_hours_per_week", 80)));
            long t1 = System.nanoTime();
            MrpResult result = MrpEngine.run(input);
            long t2 = System.nanoTime();
            sourceData.insertProposals(run.getId(), userId, result.proposals());
            long t3 = System.nanoTime();
            complete(run, result);
            log.info("MRP run {}: load {} ms, plan {} ms, save {} ms, {} proposals", run.getRunNumber(),
                    (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000, result.proposals().size());
        } catch (RuntimeException e) {
            log.error("MRP run {} failed", run.getRunNumber(), e);
            failureRecorder.record(userId, horizon, safety, e.getMessage());
            throw e;
        }
        run = runRepo.save(run);
        auditService.record("MRP_RUN", "MrpRun", String.valueOf(run.getId()), null,
                run.getRunNumber() + " proposals=" + run.getTotalProposals(), null);
        return toDto(run);
    }

    @Transactional(readOnly = true)
    public Page<MrpRunDto> findAll(Pageable pageable) {
        return runRepo.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public MrpRunDto findById(Long id) {
        return toDto(require(id));
    }

    MrpRun require(Long id) {
        return runRepo.findById(id).orElseThrow(() -> new NotFoundException("MRP run not found: " + id));
    }

    private void complete(MrpRun run, MrpResult result) {
        List<ProposalDraft> proposals = result.proposals();
        run.setStatus("COMPLETED");
        run.setCompletedAt(Instant.now());
        run.setTotalProposals(proposals.size());
        run.setCapacityOverloads((int) proposals.stream().filter(ProposalDraft::capacityOverload).count());
        if (!result.warnings().isEmpty()) {
            String notes = String.join("; ", result.warnings());
            run.setNotes(notes.length() > 1000 ? notes.substring(0, 997) + "..." : notes);
        }
    }

    private MrpRunDto toDto(MrpRun r) {
        return new MrpRunDto(r.getId(), r.getRunNumber(), r.getInitiatedAt(), r.getCompletedAt(),
                r.getPlanningHorizonDays(), r.isIncludeSafetyStock(), r.getStatus(), r.getInitiatedBy(),
                r.getTotalProposals(), r.getCapacityOverloads(), r.getNotes(), r.getErrorMessage());
    }
}
