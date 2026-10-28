package com.mfg.ims.production.barcode;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production/barcodes")
public class BarcodeController {

    private final BarcodeService service;

    public BarcodeController(BarcodeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BARCODE_VIEW')")
    public List<BarcodeLabelDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BARCODE_VIEW')")
    public BarcodeLabelDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BARCODE_MANAGE')")
    public BarcodeLabelDto generate(@Valid @RequestBody GenerateBarcodeRequest request,
                                    @AuthenticationPrincipal CachedUser actor) {
        return service.generate(request, actor.id());
    }

    @PostMapping("/{id}/print")
    @PreAuthorize("hasAuthority('BARCODE_VIEW')")
    public BarcodeLabelDto recordPrint(@PathVariable Long id,
                                       @AuthenticationPrincipal CachedUser actor) {
        return service.recordPrint(id, actor.id());
    }
}
