package com.mfg.ims.mrp.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.mrp.domain.MrpRun;
import com.mfg.ims.mrp.repository.MrpRunRepository;

/** Records a FAILED run in its own transaction, so the evidence survives the rollback of the planning transaction. */
@Service
public class MrpRunFailureRecorder {

    private final MrpRunRepository runRepo;
    private final DocumentNumberService numberService;

    public MrpRunFailureRecorder(MrpRunRepository runRepo, DocumentNumberService numberService) {
        this.runRepo = runRepo;
        this.numberService = numberService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, int horizonDays, boolean includeSafetyStock, String message) {
        MrpRun run = new MrpRun();
        run.setRunNumber(numberService.next("MRP"));
        run.setPlanningHorizonDays(horizonDays);
        run.setIncludeSafetyStock(includeSafetyStock);
        run.setInitiatedBy(userId);
        run.setStatus("FAILED");
        run.setCompletedAt(Instant.now());
        run.setErrorMessage(message == null ? "Unknown error" : message.substring(0, Math.min(message.length(), 500)));
        runRepo.save(run);
    }
}
