package com.mfg.ims.controlling.web;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.controlling.dto.*;
import com.mfg.ims.controlling.service.CostAllocationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/controlling/overhead-allocation")
public class CostAllocationController {

    private final CostAllocationService service;

    public CostAllocationController(CostAllocationService service) {
        this.service = service;
    }

    // ── Rules ─────────────────────────────────────────────────────────────────

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('OVERHEAD_ALLOC_VIEW')")
    public List<CostAllocationRuleDto> getRules() {
        return service.findAllRules();
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('OVERHEAD_ALLOC_RUN')")
    public CostAllocationRuleDto createRule(@Valid @RequestBody SaveAllocationRuleRequest req) {
        return service.createRule(req);
    }

    @PostMapping("/rules/{id}/activate")
    @PreAuthorize("hasAuthority('OVERHEAD_ALLOC_RUN')")
    public CostAllocationRuleDto activate(@PathVariable Long id) {
        return service.setRuleActive(id, true);
    }

    @PostMapping("/rules/{id}/deactivate")
    @PreAuthorize("hasAuthority('OVERHEAD_ALLOC_RUN')")
    public CostAllocationRuleDto deactivate(@PathVariable Long id) {
        return service.setRuleActive(id, false);
    }

    // ── Runs ──────────────────────────────────────────────────────────────────

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('OVERHEAD_ALLOC_VIEW')")
    public List<AllocationRunDto> getRuns() {
        return service.findRuns();
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("hasAuthority('OVERHEAD_ALLOC_VIEW')")
    public AllocationRunDto getRun(@PathVariable Long id) {
        return service.findRunById(id);
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('OVERHEAD_ALLOC_RUN')")
    public AllocationRunDto run(@RequestParam Long periodId,
                                @AuthenticationPrincipal CachedUser principal) {
        return service.runAllocation(periodId, principal != null ? principal.id() : 1L);
    }
}
