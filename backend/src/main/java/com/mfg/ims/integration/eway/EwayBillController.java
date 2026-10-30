package com.mfg.ims.integration.eway;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/integration/eway-bill")
public class EwayBillController {

    private final EwayBillService service;

    public EwayBillController(EwayBillService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EWAY_BILL_MANAGE')")
    public List<EwayBillDtos.EwayBillDto> list(@RequestParam(required = false) Long invoiceId) {
        return service.list(invoiceId);
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EWAY_BILL_MANAGE')")
    public EwayBillDtos.EwayBillDto generate(@Valid @RequestBody EwayBillDtos.GenerateRequest req,
                                              @AuthenticationPrincipal CachedUser actor) {
        return service.generate(req, actor.id());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('EWAY_BILL_MANAGE')")
    public EwayBillDtos.EwayBillDto submit(@PathVariable Long id) {
        return service.submit(id);
    }
}
