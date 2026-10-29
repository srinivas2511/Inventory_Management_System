package com.mfg.ims.procurement.purchasereturnchallan;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/procurement/purchase-return-challans")
public class PurchaseReturnChallanController {

    private final PurchaseReturnChallanService service;

    public PurchaseReturnChallanController(PurchaseReturnChallanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRC_CHALLAN_VIEW')")
    public List<PurchaseReturnChallanDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRC_CHALLAN_VIEW')")
    public PurchaseReturnChallanDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRC_CHALLAN_CREATE')")
    public PurchaseReturnChallanDto create(@Valid @RequestBody SaveChallanRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('PRC_CHALLAN_DISPATCH')")
    public PurchaseReturnChallanDto dispatch(@PathVariable Long id) {
        return service.dispatch(id);
    }
}
