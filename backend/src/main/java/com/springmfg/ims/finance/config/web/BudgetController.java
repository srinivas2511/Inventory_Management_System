package com.springmfg.ims.finance.config.web;

import com.springmfg.ims.finance.config.dto.BudgetDto;
import com.springmfg.ims.finance.config.dto.BudgetRequest;
import com.springmfg.ims.finance.config.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BUDGET_MANAGE')")
    public BudgetDto saveBudget(@Valid @RequestBody BudgetRequest req) {
        return budgetService.saveBudget(req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BUDGET_MANAGE')")
    public List<BudgetDto> listByYear(@RequestParam Integer fiscalYear) {
        return budgetService.findByFiscalYear(fiscalYear);
    }
}
