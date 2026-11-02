package com.mfg.ims.masterdata.workcentre;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.ConflictException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;

@Service
public class WorkCentreService {

    private final WorkCentreRepository repo;
    private final AuditService auditService;

    public WorkCentreService(WorkCentreRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<WorkCentreDto> findAll() {
        return repo.findAllByOrderByCode().stream().map(WorkCentreDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkCentreDto> findActive() {
        return repo.findAllByActiveTrueOrderByCode().stream().map(WorkCentreDto::from).toList();
    }

    @Transactional(readOnly = true)
    public WorkCentreDto findById(Long id) {
        return WorkCentreDto.from(require(id));
    }

    @Transactional
    public WorkCentreDto create(SaveWorkCentreRequest req) {
        if (repo.existsByCode(req.code())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Work centre code already exists: " + req.code());
        }
        WorkCentre wc = new WorkCentre();
        apply(wc, req);
        repo.save(wc);
        auditService.record("CREATE", "WORK_CENTRE", String.valueOf(wc.getId()), null, wc.getCode(), null);
        return WorkCentreDto.from(wc);
    }

    @Transactional
    public WorkCentreDto update(Long id, SaveWorkCentreRequest req) {
        WorkCentre wc = require(id);
        String old = wc.getCode();
        apply(wc, req);
        repo.save(wc);
        auditService.record("UPDATE", "WORK_CENTRE", String.valueOf(id), old, wc.getName(), null);
        return WorkCentreDto.from(wc);
    }

    private void apply(WorkCentre wc, SaveWorkCentreRequest req) {
        wc.setCode(req.code());
        wc.setName(req.name());
        wc.setDescription(req.description());
        wc.setActive(req.active());
    }

    private WorkCentre require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Work centre not found: " + id));
    }
}
