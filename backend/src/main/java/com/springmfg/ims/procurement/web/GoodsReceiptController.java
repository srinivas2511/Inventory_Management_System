package com.springmfg.ims.procurement.web;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.procurement.dto.CreateGrRequest;
import com.springmfg.ims.procurement.dto.GoodsReceiptDto;
import com.springmfg.ims.procurement.service.GoodsReceiptService;

@RestController
@RequestMapping("/api/procurement")
public class GoodsReceiptController {

    private final GoodsReceiptService service;

    public GoodsReceiptController(GoodsReceiptService service) {
        this.service = service;
    }

    @PostMapping("/goods-receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_RECEIVE')")
    public GoodsReceiptDto receive(@Valid @RequestBody CreateGrRequest req) {
        return service.receive(req);
    }

    @GetMapping("/goods-receipts/{id}")
    @PreAuthorize("hasAnyAuthority('PURCHASE_VIEW','INVENTORY_RECEIVE')")
    public GoodsReceiptDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/purchase-orders/{poId}/goods-receipts")
    @PreAuthorize("hasAnyAuthority('PURCHASE_VIEW','INVENTORY_RECEIVE')")
    public Page<GoodsReceiptDto> listByPo(
            @PathVariable Long poId,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.findByPurchaseOrder(poId, pageable);
    }
}
