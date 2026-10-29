package com.mfg.ims.integration.tally;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/integration/tally/ledger-mappings")
public class TallyLedgerMappingController {

    private final TallyLedgerMappingService service;

    public TallyLedgerMappingController(TallyLedgerMappingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public List<TallyLedgerMappingDto> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public TallyLedgerMappingDto create(@RequestBody CreateTallyLedgerMappingRequest req,
                                         @AuthenticationPrincipal CachedUser actor) {
        return service.create(req, actor.id());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public TallyLedgerMappingDto update(@PathVariable Long id,
                                         @RequestBody CreateTallyLedgerMappingRequest req,
                                         @AuthenticationPrincipal CachedUser actor) {
        return service.update(id, req, actor.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
