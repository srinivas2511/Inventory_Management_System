package com.mfg.ims.quality.web;

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

import com.mfg.ims.quality.dto.AcceptInspectionRequest;
import com.mfg.ims.quality.dto.ApproveFgHoldRequest;
import com.mfg.ims.quality.dto.CreateFinalInspectionRequest;
import com.mfg.ims.quality.dto.CreateInProcessInspectionRequest;
import com.mfg.ims.quality.dto.DeviationOverrideRequest;
import com.mfg.ims.quality.dto.FgBatchDto;
import com.mfg.ims.quality.dto.FinalInspectionDecisionRequest;
import com.mfg.ims.quality.dto.InspectionResultRequest;
import com.mfg.ims.quality.dto.QualityInspectionDto;
import com.mfg.ims.quality.dto.SupplierPerformanceDto;
import com.mfg.ims.quality.service.FinalInspectionService;
import com.mfg.ims.quality.service.InspectionService;
import com.mfg.ims.quality.service.SupplierPerformanceService;

@RestController
@RequestMapping("/api/quality")
public class InspectionController {

    private final InspectionService service;
    private final FinalInspectionService finalService;
    private final SupplierPerformanceService performanceService;

    public InspectionController(InspectionService service,
            FinalInspectionService finalService,
            SupplierPerformanceService performanceService) {
        this.service = service;
        this.finalService = finalService;
        this.performanceService = performanceService;
    }

    public record RejectRequest(@NotBlank String reason) {}

    // ── Incoming inspections (Phase 3) ────────────────────────────────────────

    @GetMapping("/inspections")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public Page<QualityInspectionDto> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
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
    public QualityInspectionDto accept(@PathVariable Long id,
            @Valid @RequestBody(required = false) AcceptInspectionRequest req) {
        return req == null ? service.accept(id) : service.accept(id, req.acceptedQty(), req.rejectedQty());
    }

    @PostMapping("/inspections/{id}/reject")
    @PreAuthorize("hasAuthority('QUALITY_INSPECT')")
    public QualityInspectionDto reject(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        return service.reject(id, req.reason());
    }

    @PostMapping("/inspections/{id}/hold")
    @PreAuthorize("hasAuthority('QUALITY_HOLD')")
    public QualityInspectionDto hold(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        return service.hold(id, req.reason());
    }

    @PostMapping("/inspections/{id}/reinspect")
    @PreAuthorize("hasAuthority('QUALITY_INSPECT')")
    public QualityInspectionDto reinspect(@PathVariable Long id) {
        return service.reinspect(id);
    }

    @GetMapping("/supplier-performance/{supplierId}")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','PURCHASE_VIEW')")
    public SupplierPerformanceDto supplierPerformance(@PathVariable Long supplierId) {
        return performanceService.supplierPerformance(supplierId);
    }

    // ── In-process inspections (Task 5.2) ────────────────────────────────────

    @PostMapping("/inspections/in-process")
    @PreAuthorize("hasAuthority('QUALITY_INSPECT')")
    public QualityInspectionDto createInProcess(@Valid @RequestBody CreateInProcessInspectionRequest req) {
        return finalService.createInProcess(req);
    }

    // ── Final inspections (Task 5.3) ──────────────────────────────────────────

    @PostMapping("/inspections/final")
    @PreAuthorize("hasAuthority('QUALITY_INSPECT')")
    public QualityInspectionDto createFinal(@Valid @RequestBody CreateFinalInspectionRequest req) {
        return finalService.createFinal(req);
    }

    @PostMapping("/inspections/{id}/final-decision")
    @PreAuthorize("hasAuthority('QUALITY_INSPECT')")
    public QualityInspectionDto completeFinal(
            @PathVariable Long id,
            @Valid @RequestBody FinalInspectionDecisionRequest req) {
        return finalService.completeFinalInspection(id, req);
    }

    // ── Deviation override (Task 5.5) ─────────────────────────────────────────

    @PostMapping("/inspections/{id}/items/{seq}/deviation")
    @PreAuthorize("hasAuthority('QUALITY_APPROVE')")
    public QualityInspectionDto deviationOverride(
            @PathVariable Long id, @PathVariable int seq,
            @Valid @RequestBody DeviationOverrideRequest req) {
        return finalService.recordDeviationOverride(id, seq, req.deviationNote());
    }

    // ── FG batch endpoints (Tasks 5.1, 5.3) ──────────────────────────────────

    @GetMapping("/fg-batches")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public Page<FgBatchDto> listFgBatches(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long productId,
            @PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return finalService.searchFgBatches(status, productId, pageable);
    }

    @GetMapping("/fg-batches/{id}")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public FgBatchDto getFgBatch(@PathVariable Long id) {
        return finalService.getFgBatch(id);
    }

    @PostMapping("/fg-batches/{id}/approve-hold")
    @PreAuthorize("hasAuthority('QUALITY_APPROVE')")
    public FgBatchDto approveHold(
            @PathVariable Long id,
            @Valid @RequestBody ApproveFgHoldRequest req) {
        return finalService.approveHold(id, req);
    }
}
