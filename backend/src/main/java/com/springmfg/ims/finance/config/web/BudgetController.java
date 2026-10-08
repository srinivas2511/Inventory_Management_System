package com.springmfg.ims.finance.config.web;

import com.springmfg.ims.finance.config.dto.BudgetDto;
import com.springmfg.ims.finance.config.dto.BudgetRequest;
import com.springmfg.ims.finance.config.dto.BudgetVsActualDtos.BudgetImportResultDto;
import com.springmfg.ims.finance.config.dto.BudgetVsActualDtos.BudgetVsActualDto;
import com.springmfg.ims.finance.config.service.BudgetService;
import com.springmfg.ims.finance.config.service.BudgetVsActualService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/config/budgets")
public class BudgetController {

    private final BudgetService budgetService;
    private final BudgetVsActualService vsActual;

    public BudgetController(BudgetService budgetService, BudgetVsActualService vsActual) {
        this.budgetService = budgetService;
        this.vsActual = vsActual;
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

    @GetMapping("/vs-actual")
    @PreAuthorize("hasAnyAuthority('BUDGET_MANAGE','GL_VIEW')")
    public BudgetVsActualDto vsActual(@RequestParam Integer fiscalYear,
                                      @RequestParam(required = false) Long periodId) {
        return vsActual.report(fiscalYear, periodId);
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('BUDGET_MANAGE')")
    public BudgetImportResultDto importCsv(@RequestParam Integer fiscalYear,
                                           @RequestParam("file") MultipartFile file) {
        return vsActual.importCsv(fiscalYear, file);
    }
}
