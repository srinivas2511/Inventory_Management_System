package com.mfg.ims.procurement.web;

import jakarta.validation.Valid;

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

import com.mfg.ims.procurement.dto.CreateRequisitionRequest;
import com.mfg.ims.procurement.dto.PurchaseRequisitionDto;
import com.mfg.ims.procurement.service.PurchaseRequisitionService;

@RestController
@RequestMapping("/api/procurement/requisitions")
public class PurchaseRequisitionController {

    private final PurchaseRequisitionService service;

    public PurchaseRequisitionController(PurchaseRequisitionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PURCHASE_VIEW','PURCHASE_CREATE')")
    public Page<PurchaseRequisitionDto> list(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return service.findByStatus(status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PURCHASE_VIEW','PURCHASE_CREATE')")
    public PurchaseRequisitionDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseRequisitionDto create(@Valid @RequestBody CreateRequisitionRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseRequisitionDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
