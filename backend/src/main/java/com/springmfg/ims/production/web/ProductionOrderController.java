package com.springmfg.ims.production.web;

import java.util.List;

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

import com.springmfg.ims.production.dto.CloseOrderRequest;
import com.springmfg.ims.production.dto.CreateProductionOrderRequest;
import com.springmfg.ims.production.dto.ProductionOperationDto;
import com.springmfg.ims.production.dto.ProductionOrderDto;
import com.springmfg.ims.production.dto.ProductionOrderMaterialDto;
import com.springmfg.ims.production.dto.ReturnMaterialRequest;
import com.springmfg.ims.production.dto.WipDto;
import com.springmfg.ims.production.service.OrderMaterialService;
import com.springmfg.ims.production.service.ProductionOrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/production-orders")
public class ProductionOrderController {

    private final ProductionOrderService service;
    private final OrderMaterialService orderMaterialService;

    public ProductionOrderController(ProductionOrderService service, OrderMaterialService orderMaterialService) {
        this.service = service;
        this.orderMaterialService = orderMaterialService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public Page<ProductionOrderDto> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long productId,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(status, productId, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCTION_CREATE')")
    public ProductionOrderDto create(@Valid @RequestBody CreateProductionOrderRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public ProductionOrderDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PRODUCTION_APPROVE')")
    public ProductionOrderDto approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority('PRODUCTION_RELEASE')")
    public ProductionOrderDto release(@PathVariable Long id) {
        return service.release(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PRODUCTION_UPDATE')")
    public ProductionOrderDto cancel(@PathVariable Long id,
                                     @RequestBody(required = false) String reason) {
        return service.cancel(id, reason);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('PRODUCTION_CLOSE')")
    public ProductionOrderDto close(@PathVariable Long id,
                                    @Valid @RequestBody(required = false) CloseOrderRequest req) {
        return service.close(id, req != null ? req.writeOffReason() : null);
    }

    @PostMapping("/{id}/material-returns")
    @PreAuthorize("hasAuthority('PRODUCTION_CLOSE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void returnMaterial(@PathVariable Long id, @Valid @RequestBody ReturnMaterialRequest req) {
        orderMaterialService.returnMaterial(id, req);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('PRODUCTION_REOPEN')")
    public ProductionOrderDto reopen(@PathVariable Long id) {
        return service.reopen(id);
    }

    @GetMapping("/{id}/materials")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<ProductionOrderMaterialDto> getMaterials(@PathVariable Long id) {
        return service.getMaterials(id);
    }

    @GetMapping("/{id}/operations")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<ProductionOperationDto> getOperations(@PathVariable Long id) {
        return service.getOperations(id);
    }

    @GetMapping("/{id}/wip")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<WipDto> getWip(@PathVariable Long id) {
        return service.getWip(id);
    }
}
