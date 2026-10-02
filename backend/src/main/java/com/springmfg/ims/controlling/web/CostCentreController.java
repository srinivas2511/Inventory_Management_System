package com.springmfg.ims.controlling.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.controlling.dto.CostCentreBudgetDto;
import com.springmfg.ims.controlling.dto.CostCentreDto;
import com.springmfg.ims.controlling.dto.SaveCostCentreBudgetRequest;
import com.springmfg.ims.controlling.dto.SaveCostCentreRequest;
import com.springmfg.ims.controlling.service.CostCentreService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/controlling/cost-centres")
public class CostCentreController {

    private final CostCentreService service;

    public CostCentreController(CostCentreService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONTROLLING_VIEW')")
    public List<CostCentreDto> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTROLLING_VIEW')")
    public CostCentreDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONTROLLING_MANAGE')")
    public CostCentreDto create(@Valid @RequestBody SaveCostCentreRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTROLLING_MANAGE')")
    public CostCentreDto update(@PathVariable Long id, @Valid @RequestBody SaveCostCentreRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('CONTROLLING_MANAGE')")
    public CostCentreDto activate(@PathVariable Long id) {
        return service.setActive(id, true);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('CONTROLLING_MANAGE')")
    public CostCentreDto deactivate(@PathVariable Long id) {
        return service.setActive(id, false);
    }

    @GetMapping("/{id}/budgets")
    @PreAuthorize("hasAuthority('CONTROLLING_VIEW')")
    public List<CostCentreBudgetDto> budgets(@PathVariable Long id, @RequestParam int fiscalYear) {
        return service.findBudgets(id, fiscalYear);
    }

    @PutMapping("/{id}/budgets")
    @PreAuthorize("hasAuthority('CONTROLLING_MANAGE')")
    public CostCentreBudgetDto saveBudget(@PathVariable Long id, @Valid @RequestBody SaveCostCentreBudgetRequest req) {
        return service.saveBudget(id, req);
    }
}
