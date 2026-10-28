package com.mfg.ims.production.costrollup;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production/cost-rollup")
public class CostRollupController {

    private final CostRollupService service;

    public CostRollupController(CostRollupService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('COST_ROLLUP_VIEW')")
    public List<CostRollupLogDto> list() {
        return service.list();
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COST_ROLLUP_RUN')")
    public List<CostRollupLogDto> run(@AuthenticationPrincipal CachedUser actor) {
        return service.runRollup(actor.id());
    }
}
