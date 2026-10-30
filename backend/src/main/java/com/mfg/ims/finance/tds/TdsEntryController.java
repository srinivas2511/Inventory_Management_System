package com.mfg.ims.finance.tds;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance/tds-entries")
public class TdsEntryController {

    private final TdsEntryService service;

    public TdsEntryController(TdsEntryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TDS_MANAGE')")
    public List<TdsEntryDto> list(@RequestParam(required = false) Long partyId) {
        return service.list(partyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TDS_MANAGE')")
    public TdsEntryDto create(@Valid @RequestBody TdsEntryDto req,
                              @AuthenticationPrincipal CachedUser actor) {
        return service.create(req, actor.id());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('TDS_MANAGE')")
    public List<TdsSummaryDto> summary() {
        return service.summary();
    }
}
