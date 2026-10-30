package com.mfg.ims.integration.tally;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/integration/tally/voucher-queue")
public class TallyVoucherQueueController {

    private final TallyVoucherQueueService service;

    public TallyVoucherQueueController(TallyVoucherQueueService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_VOUCHER_VIEW')")
    public List<TallyVoucherQueueDto> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ACCOUNTING_VOUCHER_APPROVE')")
    public TallyVoucherQueueDto approve(@PathVariable Long id,
                                         @AuthenticationPrincipal CachedUser actor) {
        return service.approve(id, actor.id());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('ACCOUNTING_VOUCHER_APPROVE')")
    public TallyVoucherQueueDto reject(@PathVariable Long id,
                                        @AuthenticationPrincipal CachedUser actor) {
        return service.reject(id, actor.id());
    }

    @PutMapping("/{id}/mark-sent")
    @PreAuthorize("hasAuthority('ACCOUNTING_VOUCHER_APPROVE')")
    public TallyVoucherQueueDto markSent(@PathVariable Long id,
                                          @AuthenticationPrincipal CachedUser actor) {
        return service.markSent(id, actor.id());
    }

    @GetMapping("/counts")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public TallyVoucherQueueService.VoucherQueueCountsDto counts() {
        return service.counts();
    }
}
