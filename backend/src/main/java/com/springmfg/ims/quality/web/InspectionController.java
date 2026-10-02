package com.springmfg.ims.quality.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.quality.dto.InspectionResultRequest;
import com.springmfg.ims.quality.dto.QualityInspectionDto;
import com.springmfg.ims.quality.dto.SupplierPerformanceDto;
import com.springmfg.ims.quality.service.InspectionService;
import com.springmfg.ims.quality.service.SupplierPerformanceService;

@RestController
@RequestMapping("/api/quality")
public class InspectionController {

    private final InspectionService service;
    private final SupplierPerformanceService performanceService;

    public InspectionController(InspectionService service, SupplierPerformanceService performanceService) {
        this.service = service;
        this.performanceService = performanceService;
    }

    public record RejectRequest(@NotBlank String reason) {}

    @GetMapping("/inspections")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public Page<QualityInspectionDto> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(type, status, pageable);
    }

    @GetMapping("/inspections/{id}")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public QualityInspectionDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping("/inspections/{id}/results")
    @PreAuthorize("hasAuthority('QUALITY_INSPECT')")
    public QualityInspectionDto submitResult(
            @PathVariable Long id,
            @Valid @RequestBody InspectionResultRequest req) {
        return service.submitResult(id, req);
    }

    @PostMapping("/inspections/{id}/accept")
    @PreAuthorize("hasAuthority('QUALITY_INSPECT')")
    public QualityInspectionDto accept(@PathVariable Long id) {
        return service.accept(id);
    }

    @PostMapping("/inspections/{id}/reject")
    @PreAuthorize("hasAuthority('QUALITY_INSPECT')")
    public QualityInspectionDto reject(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        return service.reject(id, req.reason());
    }

    @GetMapping("/supplier-performance/{supplierId}")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','PURCHASE_VIEW')")
    public SupplierPerformanceDto supplierPerformance(@PathVariable Long supplierId) {
        return performanceService.supplierPerformance(supplierId);
    }
}
