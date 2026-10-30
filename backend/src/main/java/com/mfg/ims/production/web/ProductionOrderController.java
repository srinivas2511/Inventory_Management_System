package com.mfg.ims.production.web;

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

import com.mfg.ims.production.dto.AssignOperatorRequest;
import com.mfg.ims.production.dto.CloseOrderRequest;
import com.mfg.ims.production.dto.CreateProductionOrderRequest;
import com.mfg.ims.production.dto.ProductionOperationDto;
import com.mfg.ims.production.dto.ProductionOrderDto;
import com.mfg.ims.production.dto.ProductionOrderMaterialDto;
import com.mfg.ims.production.dto.QuickCreateRequest;
import com.mfg.ims.production.dto.ReturnMaterialRequest;
import com.mfg.ims.production.dto.ShopFloorMaterialDto;
import com.mfg.ims.production.dto.UpdateRoutingRequest;
import com.mfg.ims.production.dto.WipDto;
import com.mfg.ims.production.service.OperationAssignmentService;
import com.mfg.ims.production.service.OrderMaterialService;
import com.mfg.ims.production.service.ProductionConvenienceService;
import com.mfg.ims.production.service.ProductionOrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/production-orders")
public class ProductionOrderController {

    private final ProductionOrderService service;
    private final OrderMaterialService orderMaterialService;
    private final OperationAssignmentService assignmentService;
    private final ProductionConvenienceService convenienceService;

    public ProductionOrderController(ProductionOrderService service, OrderMaterialService orderMaterialService,
                                     OperationAssignmentService assignmentService,
                                     ProductionConvenienceService convenienceService) {
        this.service = service;
        this.orderMaterialService = orderMaterialService;
        this.assignmentService = assignmentService;
        this.convenienceService = convenienceService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public Page<ProductionOrderDto> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long productId,
            @PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
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

    @PutMapping("/{id}/operator-assignment")
    @PreAuthorize("hasAuthority('PRODUCTION_ASSIGN')")
    public List<ProductionOperationDto> assignOperator(@PathVariable Long id,
                                                       @Valid @RequestBody AssignOperatorRequest req) {
        return assignmentService.assignAll(id, req.operatorId(), req.reason());
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

    // ---- 13.9 convenience endpoints ----

    @PostMapping("/{id}/complete-all")
    @PreAuthorize("hasAuthority('PRODUCTION_MANAGE')")
    public ProductionOrderDto completeAll(@PathVariable Long id) {
        return convenienceService.completeAll(id);
    }

    @PostMapping("/quick-create")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCTION_MANAGE')")
    public ProductionOrderDto quickCreate(@Valid @RequestBody QuickCreateRequest req) {
        return convenienceService.quickCreate(req);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/routing")
    @PreAuthorize("hasAuthority('PRODUCTION_MANAGE')")
    public List<ProductionOperationDto> updateRouting(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateRoutingRequest req) {
        return convenienceService.updateRouting(id, req);
    }

    @GetMapping("/{id}/shop-floor-materials")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<ShopFloorMaterialDto> shopFloorMaterials(@PathVariable Long id) {
        return convenienceService.shopFloorMaterials(id);
    }
}
