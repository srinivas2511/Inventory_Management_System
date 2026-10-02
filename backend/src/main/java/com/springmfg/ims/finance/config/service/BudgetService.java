package com.springmfg.ims.finance.config.service;

import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.finance.config.domain.Budget;
import com.springmfg.ims.finance.config.dto.BudgetDto;
import com.springmfg.ims.finance.config.dto.BudgetRequest;
import com.springmfg.ims.finance.config.repository.BudgetRepository;
import com.springmfg.ims.finance.gl.domain.GlAccount;
import com.springmfg.ims.finance.gl.repository.GlAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class BudgetService {

    private final BudgetRepository budgetRepo;
    private final GlAccountRepository glAccountRepo;

    public BudgetService(BudgetRepository budgetRepo, GlAccountRepository glAccountRepo) {
        this.budgetRepo = budgetRepo;
        this.glAccountRepo = glAccountRepo;
    }

    public BudgetDto saveBudget(BudgetRequest req) {
        Budget budget = budgetRepo.findByGlAccountIdAndFiscalYearAndPeriodNumber(
                req.glAccountId(), req.fiscalYear(), req.periodNumber())
                .orElseGet(Budget::new);
        budget.setGlAccountId(req.glAccountId());
        budget.setFiscalYear(req.fiscalYear());
        budget.setPeriodNumber(req.periodNumber());
        budget.setBudgetedAmount(req.budgetedAmount());
        budget.setCostCenter(req.costCenter());
        return toDto(budgetRepo.save(budget));
    }

    @Transactional(readOnly = true)
    public List<BudgetDto> findByFiscalYear(Integer fiscalYear) {
        return budgetRepo.findByFiscalYear(fiscalYear).stream().map(this::toDto).toList();
    }

    private BudgetDto toDto(Budget b) {
        GlAccount acc = glAccountRepo.findById(b.getGlAccountId()).orElse(null);
        return new BudgetDto(b.getId(), b.getGlAccountId(),
                acc != null ? acc.getAccountCode() : "",
                acc != null ? acc.getName() : "",
                b.getFiscalYear(), b.getPeriodNumber(), b.getBudgetedAmount(), b.getCostCenter());
    }
}
