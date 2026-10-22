package com.mfg.ims.finance.config.service;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.finance.config.domain.Budget;
import com.mfg.ims.finance.config.dto.BudgetDto;
import com.mfg.ims.finance.config.dto.BudgetRequest;
import com.mfg.ims.finance.config.repository.BudgetRepository;
import com.mfg.ims.finance.gl.domain.GlAccount;
import com.mfg.ims.finance.gl.repository.GlAccountRepository;
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
        Budget budget = budgetRepo.findByGlAccountIdAndFiscalYearAndPeriodId(
                req.glAccountId(), req.fiscalYear(), req.periodId())
                .orElseGet(Budget::new);
        budget.setGlAccountId(req.glAccountId());
        budget.setFiscalYear(req.fiscalYear());
        budget.setPeriodId(req.periodId());
        budget.setBudgetedAmount(req.budgetedAmount());
        budget.setBudgetName(req.budgetName() != null ? req.budgetName() : "FY" + req.fiscalYear() + " budget");
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
                b.getFiscalYear(), b.getPeriodId(), b.getBudgetedAmount(), b.getBudgetName());
    }
}
