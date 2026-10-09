package com.springmfg.ims.inventory.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

import com.springmfg.ims.inventory.dto.StockAdjustmentDto;
import com.springmfg.ims.inventory.service.StockAdjustmentService;

@RestController
@RequestMapping("/api/inventory/adjustments")
public class StockAdjustmentController {

    private final StockAdjustmentService service;

    public StockAdjustmentController(StockAdjustmentService service) {
        this.service = service;
    }

    public record AdjustmentRequest(
            @NotBlank String itemType,
            Long materialId,
            Long productId,
            Long batchId,
            @NotNull Long warehouseId,
            @NotNull Long locationId,
            @NotBlank String stockStatus,
            @NotNull @Positive BigDecimal countedQty,
            @NotBlank String reason) {}

    public record DecisionRequest(@NotBlank String comment) {}

    @GetMapping
    @PreAuthorize("hasAnyAuthority('INVENTORY_VIEW','INVENTORY_ADJUST','ADJUST_REVIEW','ADJUST_APPROVE')")
    public Page<StockAdjustmentDto> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long warehouseId,
            @PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return service.search(status, warehouseId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('INVENTORY_VIEW','INVENTORY_ADJUST')")
    public StockAdjustmentDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_ADJUST')")
    public StockAdjustmentDto request(@Valid @RequestBody AdjustmentRequest req) {
        return service.request(req.itemType(), req.materialId(), req.productId(),
                req.batchId(), req.warehouseId(), req.locationId(),
                req.stockStatus(), req.countedQty(), req.reason());
    }

    @PostMapping("/approval-requests/{approvalId}/approve")
    @PreAuthorize("hasAnyAuthority('ADJUST_REVIEW','ADJUST_APPROVE','ADJUST_APPROVE_ADMIN')")
    public void approve(@PathVariable Long approvalId, @Valid @RequestBody DecisionRequest req) {
        service.approveStep(approvalId, req.comment());
    }

    @PostMapping("/approval-requests/{approvalId}/reject")
    @PreAuthorize("hasAnyAuthority('ADJUST_REVIEW','ADJUST_APPROVE','ADJUST_APPROVE_ADMIN')")
    public void reject(@PathVariable Long approvalId, @Valid @RequestBody DecisionRequest req) {
        service.rejectStep(approvalId, req.comment());
    }
}
