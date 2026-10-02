package com.springmfg.ims.controlling.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.controlling.domain.ProfitCentre;
import com.springmfg.ims.controlling.dto.ProfitCentreDto;
import com.springmfg.ims.controlling.dto.SaveProfitCentreRequest;
import com.springmfg.ims.controlling.repository.ProfitCentreRepository;

@Service
public class ProfitCentreService {

    private final ProfitCentreRepository repo;
    private final AuditService auditService;

    public ProfitCentreService(ProfitCentreRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ProfitCentreDto> findAll() {
        return repo.findAllByOrderByProfitCentreCode().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProfitCentreDto findById(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public ProfitCentreDto create(SaveProfitCentreRequest req) {
        if (repo.existsByProfitCentreCode(req.profitCentreCode())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Profit centre code already exists: " + req.profitCentreCode());
        }
        ProfitCentre pc = new ProfitCentre();
        pc.setProfitCentreCode(req.profitCentreCode());
        pc.setName(req.name());
        pc = repo.save(pc);
        auditService.record("PROFIT_CENTRE_CREATE", "ProfitCentre", String.valueOf(pc.getId()),
                null, pc.getProfitCentreCode(), null);
        return toDto(pc);
    }

    @Transactional
    public ProfitCentreDto update(Long id, SaveProfitCentreRequest req) {
        ProfitCentre pc = find(id);
        if (!pc.getProfitCentreCode().equals(req.profitCentreCode())
                && repo.existsByProfitCentreCode(req.profitCentreCode())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Profit centre code already exists: " + req.profitCentreCode());
        }
        String old = pc.getProfitCentreCode() + "|" + pc.getName();
        pc.setProfitCentreCode(req.profitCentreCode());
        pc.setName(req.name());
        pc = repo.save(pc);
        auditService.record("PROFIT_CENTRE_UPDATE", "ProfitCentre", String.valueOf(id),
                old, pc.getProfitCentreCode() + "|" + pc.getName(), null);
        return toDto(pc);
    }

    @Transactional
    public ProfitCentreDto setActive(Long id, boolean active) {
        ProfitCentre pc = find(id);
        boolean old = pc.isActive();
        pc.setActive(active);
        pc = repo.save(pc);
        auditService.record("PROFIT_CENTRE_ACTIVE", "ProfitCentre", String.valueOf(id),
                String.valueOf(old), String.valueOf(active), null);
        return toDto(pc);
    }

    private ProfitCentre find(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("ProfitCentre", id));
    }

    private ProfitCentreDto toDto(ProfitCentre pc) {
        return new ProfitCentreDto(pc.getId(), pc.getProfitCentreCode(), pc.getName(), pc.isActive());
    }
}
