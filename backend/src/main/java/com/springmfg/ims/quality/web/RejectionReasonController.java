package com.springmfg.ims.quality.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.quality.dto.ParetoEntryDto;
import com.springmfg.ims.quality.dto.RejectionDto;
import com.springmfg.ims.quality.dto.RejectionReasonDto;
import com.springmfg.ims.quality.dto.SaveRejectionReasonRequest;
import com.springmfg.ims.quality.service.RejectionReasonService;

@RestController
@RequestMapping("/api/rejection-reasons")
public class RejectionReasonController {

    private final RejectionReasonService service;

    public RejectionReasonController(RejectionReasonService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public List<RejectionReasonDto> list() {
        return service.listActive();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public RejectionReasonDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public RejectionReasonDto create(@Valid @RequestBody SaveRejectionReasonRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public RejectionReasonDto update(@PathVariable Long id, @Valid @RequestBody SaveRejectionReasonRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }

    // ── Rejection log and analytics (Task 5.6) ────────────────────────────────

    @GetMapping("/rejections")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public Page<RejectionDto> listRejections(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long reasonId,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.searchRejections(productId, reasonId, pageable);
    }

    @GetMapping("/pareto")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public List<ParetoEntryDto> pareto() {
        return service.pareto();
    }
}
