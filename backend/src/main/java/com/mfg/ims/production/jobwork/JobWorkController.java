package com.mfg.ims.production.jobwork;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production/job-work-orders")
public class JobWorkController {

    private final JobWorkService service;

    public JobWorkController(JobWorkService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('JWO_VIEW')")
    public List<JobWorkOrderDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('JWO_VIEW')")
    public JobWorkOrderDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('JWO_CREATE')")
    public JobWorkOrderDto create(@RequestBody CreateJobWorkOrderRequest req,
                                  @AuthenticationPrincipal CachedUser actor) {
        return service.create(req, actor.id());
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('JWO_ISSUE')")
    public JobWorkOrderDto issue(@PathVariable Long id,
                                  @AuthenticationPrincipal CachedUser actor) {
        return service.issue(id, actor.id());
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('JWO_RECEIVE')")
    public JobWorkOrderDto receive(@PathVariable Long id,
                                    @AuthenticationPrincipal CachedUser actor) {
        return service.receive(id, actor.id());
    }
}
