package com.springmfg.ims.controlling.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.controlling.domain.CostCentre;
import com.springmfg.ims.controlling.domain.CostCentreBudget;
import com.springmfg.ims.controlling.dto.CostCentreBudgetDto;
import com.springmfg.ims.controlling.dto.CostCentreDto;
import com.springmfg.ims.controlling.dto.SaveCostCentreBudgetRequest;
import com.springmfg.ims.controlling.dto.SaveCostCentreRequest;
import com.springmfg.ims.controlling.repository.CostCentreBudgetRepository;
import com.springmfg.ims.controlling.repository.CostCentreRepository;
import com.springmfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.springmfg.ims.finance.gl.repository.GlAccountRepository;

@Service
public class CostCentreService {

    private final CostCentreRepository repo;
    private final CostCentreBudgetRepository budgetRepo;
    private final GlAccountRepository glAccountRepo;
    private final FiscalPeriodRepository periodRepo;
    private final AuditService auditService;

    public CostCentreService(CostCentreRepository repo,
                             CostCentreBudgetRepository budgetRepo,
                             GlAccountRepository glAccountRepo,
                             FiscalPeriodRepository periodRepo,
                             AuditService auditService) {
        this.repo = repo;
        this.budgetRepo = budgetRepo;
        this.glAccountRepo = glAccountRepo;
        this.periodRepo = periodRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CostCentreDto> findAll() {
        return repo.findAllByOrderByCostCentreCode().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CostCentreDto findById(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public CostCentreDto create(SaveCostCentreRequest req) {
        if (repo.existsByCostCentreCode(req.costCentreCode())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Cost centre code already exists: " + req.costCentreCode());
        }
        CostCentre cc = new CostCentre();
        apply(cc, req);
        assertParentValid(cc, req.parentId());
        cc = repo.save(cc);
        auditService.record("COST_CENTRE_CREATE", "CostCentre", String.valueOf(cc.getId()),
                null, cc.getCostCentreCode(), null);
        return toDto(cc);
    }

    @Transactional
    public CostCentreDto update(Long id, SaveCostCentreRequest req) {
        CostCentre cc = find(id);
        if (!cc.getCostCentreCode().equals(req.costCentreCode()) && repo.existsByCostCentreCode(req.costCentreCode())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Cost centre code already exists: " + req.costCentreCode());
        }
        String old = describe(cc);
        apply(cc, req);
        assertParentValid(cc, req.parentId());
        cc = repo.save(cc);
        auditService.record("COST_CENTRE_UPDATE", "CostCentre", String.valueOf(id), old, describe(cc), null);
        return toDto(cc);
    }

    @Transactional
    public CostCentreDto setActive(Long id, boolean active) {
        CostCentre cc = find(id);
        if (!active && repo.existsByParentIdAndActiveTrue(id)) {
            throw new BusinessRuleException(ErrorCode.DEACTIVATION_BLOCKED,
                    "Cost centre " + cc.getCostCentreCode() + " has active child cost centres");
        }
        boolean old = cc.isActive();
        cc.setActive(active);
        cc = repo.save(cc);
        auditService.record("COST_CENTRE_ACTIVE", "CostCentre", String.valueOf(id),
                String.valueOf(old), String.valueOf(active), null);
        return toDto(cc);
    }

    @Transactional(readOnly = true)
    public List<CostCentreBudgetDto> findBudgets(Long costCentreId, int fiscalYear) {
        find(costCentreId);
        return budgetRepo.findByCostCentreAndFiscalYear(costCentreId, fiscalYear).stream()
                .map(this::toBudgetDto).toList();
    }

    @Transactional
    public CostCentreBudgetDto saveBudget(Long costCentreId, SaveCostCentreBudgetRequest req) {
        find(costCentreId);
        glAccountRepo.findById(req.glAccountId())
                .orElseThrow(() -> new NotFoundException("GlAccount", req.glAccountId()));
        periodRepo.findById(req.periodId())
                .orElseThrow(() -> new NotFoundException("FiscalPeriod", req.periodId()));

        CostCentreBudget budget = budgetRepo
                .findByCostCentreIdAndGlAccountIdAndPeriodId(costCentreId, req.glAccountId(), req.periodId())
                .orElseGet(() -> {
                    CostCentreBudget b = new CostCentreBudget();
                    b.setCostCentreId(costCentreId);
                    b.setGlAccountId(req.glAccountId());
                    b.setPeriodId(req.periodId());
                    return b;
                });
        String old = budget.getBudgetedAmount() == null ? null : budget.getBudgetedAmount().toPlainString();
        budget.setBudgetedAmount(req.budgetedAmount());
        budget = budgetRepo.save(budget);
        auditService.record("COST_CENTRE_BUDGET_SAVE", "CostCentre", String.valueOf(costCentreId),
                old, req.budgetedAmount().toPlainString(),
                "gl=" + req.glAccountId() + " period=" + req.periodId());
        return toBudgetDto(budget);
    }

    private CostCentre find(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("CostCentre", id));
    }

    private void apply(CostCentre cc, SaveCostCentreRequest req) {
        cc.setCostCentreCode(req.costCentreCode());
        cc.setName(req.name());
        cc.setParentId(req.parentId());
        cc.setResponsibleUserId(req.responsibleUserId());
        cc.setPlantId(req.plantId());
        cc.setLabourRatePerHour(req.labourRatePerHour());
        cc.setMachineRatePerHour(req.machineRatePerHour());
        cc.setOverheadPct(req.overheadPct());
    }

    /** Rejects a parent that would make the hierarchy cyclic (including a cost centre as its own parent). */
    private void assertParentValid(CostCentre cc, Long parentId) {
        Long cursor = parentId;
        int guard = 0;
        while (cursor != null && guard++ < 100) {
            if (cursor.equals(cc.getId())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Cost centre hierarchy cannot contain a cycle");
            }
            Long current = cursor;
            cursor = repo.findById(current)
                    .orElseThrow(() -> new NotFoundException("CostCentre", current))
                    .getParentId();
        }
    }

    private String describe(CostCentre cc) {
        return cc.getCostCentreCode() + "|" + cc.getName() + "|lab=" + cc.getLabourRatePerHour()
                + "|mac=" + cc.getMachineRatePerHour() + "|oh=" + cc.getOverheadPct();
    }

    private CostCentreDto toDto(CostCentre cc) {
        return new CostCentreDto(cc.getId(), cc.getCostCentreCode(), cc.getName(), cc.getParentId(),
                cc.getResponsibleUserId(), cc.getPlantId(), cc.getLabourRatePerHour(),
                cc.getMachineRatePerHour(), cc.getOverheadPct(), cc.isActive());
    }

    private CostCentreBudgetDto toBudgetDto(CostCentreBudget b) {
        return new CostCentreBudgetDto(b.getId(), b.getCostCentreId(), b.getGlAccountId(),
                b.getPeriodId(), b.getBudgetedAmount());
    }
}
