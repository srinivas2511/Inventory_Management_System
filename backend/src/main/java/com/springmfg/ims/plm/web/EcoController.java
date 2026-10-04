package com.springmfg.ims.plm.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import com.springmfg.ims.plm.domain.EcoStatus;
import com.springmfg.ims.plm.dto.EcoDtos.EcoDto;
import com.springmfg.ims.plm.dto.EcoDtos.EcoSummaryDto;
import com.springmfg.ims.plm.dto.EcoDtos.ReasonRequest;
import com.springmfg.ims.plm.dto.EcoDtos.SaveEcoRequest;
import com.springmfg.ims.plm.service.EcoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ecos")
public class EcoController {

    private final EcoService service;

    public EcoController(EcoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PLM_VIEW')")
    public Page<EcoSummaryDto> list(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) EcoStatus status,
                                    @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
                                    Pageable pageable) {
        return service.search(q, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PLM_VIEW')")
    public EcoDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ECO_CREATE')")
    public EcoDto create(@Valid @RequestBody SaveEcoRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ECO_CREATE')")
    public EcoDto update(@PathVariable Long id, @Valid @RequestBody SaveEcoRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('ECO_CREATE')")
    public EcoDto submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('ECO_APPROVE')")
    public EcoDto returnToDraft(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        return service.returnToDraft(id, req.reason());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ECO_APPROVE')")
    public EcoDto approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ECO_CREATE','ECO_APPROVE')")
    public EcoDto cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        return service.cancel(id, req.reason());
    }

    @PostMapping("/{id}/implement")
    @PreAuthorize("hasAuthority('ECO_CREATE')")
    public EcoDto implement(@PathVariable Long id) {
        return service.implement(id);
    }
}
