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

    /** Issue specific materials to sub-contractor with inventory OUT transactions. */
    @PostMapping("/{id}/issue-materials")
    @PreAuthorize("hasAuthority('JWO_ISSUE')")
    public JobWorkOrderDto issueMaterials(@PathVariable Long id,
                                          @RequestBody IssueMaterialsRequest req,
                                          @AuthenticationPrincipal CachedUser actor) {
        return service.issueMaterials(id, req, actor.id());
    }

    /** Receive processed goods back from sub-contractor. */
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('JWO_RECEIVE')")
    public JobWorkOrderDto receive(@PathVariable Long id,
                                    @RequestBody(required = false) ReceiveJobWorkRequest req,
                                    @AuthenticationPrincipal CachedUser actor) {
        return service.receive(id, req, actor.id());
    }

    /** Record job work service charges for AP invoice matching. */
    @PostMapping("/{id}/record-charges")
    @PreAuthorize("hasAuthority('JWO_CREATE')")
    public JobWorkChargeDto recordCharges(@PathVariable Long id,
                                          @RequestBody RecordChargesRequest req,
                                          @AuthenticationPrincipal CachedUser actor) {
        return service.recordCharges(id, req, actor.id());
    }
}
