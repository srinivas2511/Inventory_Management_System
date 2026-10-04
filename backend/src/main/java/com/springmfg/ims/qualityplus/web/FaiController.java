package com.springmfg.ims.qualityplus.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.qualityplus.domain.FaiStatus;
import com.springmfg.ims.qualityplus.dto.FaiDtos.CompleteFaiRequest;
import com.springmfg.ims.qualityplus.dto.FaiDtos.CreateFaiRequest;
import com.springmfg.ims.qualityplus.dto.FaiDtos.FaiDto;
import com.springmfg.ims.qualityplus.dto.FaiDtos.FaiReportDto;
import com.springmfg.ims.qualityplus.dto.FaiDtos.FaiSummaryDto;
import com.springmfg.ims.qualityplus.dto.FaiDtos.StartFaiRequest;
import com.springmfg.ims.qualityplus.dto.FaiDtos.SubmitFaiResultsRequest;
import com.springmfg.ims.qualityplus.service.FaiService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/fai")
public class FaiController {

    private final FaiService service;

    public FaiController(FaiService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public Page<FaiSummaryDto> list(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) FaiStatus status,
                                    @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
                                    Pageable pageable) {
        return service.search(q, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public FaiDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/report")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public FaiReportDto report(@PathVariable Long id) {
        return service.report(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('QUALITY_INSPECT')")
    public FaiDto create(@Valid @RequestBody CreateFaiRequest req) {
        return service.createManual(req.productId());
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('QUALITY_INSPECT')")
    public FaiDto start(@PathVariable Long id, @Valid @RequestBody StartFaiRequest req) {
        return service.start(id, req);
    }

    @PostMapping("/{id}/results")
    @PreAuthorize("hasAuthority('QUALITY_INSPECT')")
    public FaiReportDto submitResults(@PathVariable Long id, @Valid @RequestBody SubmitFaiResultsRequest req) {
        return service.submitResults(id, req);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('QUALITY_APPROVE')")
    public FaiDto complete(@PathVariable Long id, @Valid @RequestBody(required = false) CompleteFaiRequest req) {
        return service.complete(id, req);
    }
}
