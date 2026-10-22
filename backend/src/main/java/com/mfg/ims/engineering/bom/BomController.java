package com.mfg.ims.engineering.bom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import jakarta.validation.Valid;

@RestController
public class BomController {

    private final BomService service;

    public BomController(BomService service) {
        this.service = service;
    }

    @GetMapping("/api/boms")
    @PreAuthorize("hasAuthority('BOM_VIEW')")
    public Page<BomDto> search(@RequestParam(required = false) Long productId,
                               @RequestParam(required = false) String status,
                               @PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return service.search(productId, status, pageable);
    }

    @PostMapping("/api/boms")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BOM_CREATE')")
    public BomDto create(@Valid @RequestBody CreateBomRequest req) {
        return service.create(req);
    }

    @GetMapping("/api/boms/{id}")
    @PreAuthorize("hasAuthority('BOM_VIEW')")
    public BomDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/api/boms/{id}")
    @PreAuthorize("hasAuthority('BOM_UPDATE')")
    public BomDto update(@PathVariable Long id, @Valid @RequestBody CreateBomRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/api/boms/{id}/submit")
    @PreAuthorize("hasAuthority('BOM_CREATE')")
    public BomDto submit(@PathVariable Long id) {
        return service.submitForReview(id);
    }

    @PostMapping("/api/boms/{id}/approve")
    @PreAuthorize("hasAuthority('BOM_APPROVE')")
    public BomDto approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/api/boms/{id}/activate")
    @PreAuthorize("hasAuthority('BOM_APPROVE')")
    public BomDto activate(@PathVariable Long id) {
        return service.activate(id);
    }

    @PostMapping("/api/boms/{id}/recall")
    @PreAuthorize("hasAuthority('BOM_CREATE')")
    public BomDto recall(@PathVariable Long id) {
        return service.recall(id);
    }

    @PostMapping("/api/boms/{id}/copy-revision")
    @PreAuthorize("hasAuthority('BOM_UPDATE')")
    public BomDto copyRevision(@PathVariable Long id) {
        return service.copyRevision(id);
    }

    @GetMapping("/api/products/{productId}/bom")
    @PreAuthorize("hasAuthority('BOM_VIEW')")
    public BomDto getActiveBom(@PathVariable Long productId) {
        return service.findActiveBomForProduct(productId);
    }
}
