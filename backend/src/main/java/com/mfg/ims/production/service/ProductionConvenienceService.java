package com.mfg.ims.production.service;

import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.engineering.bom.Bom;
import com.mfg.ims.engineering.bom.BomRepository;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.production.domain.ProductionOperation;
import com.mfg.ims.production.domain.ProductionOrder;
import com.mfg.ims.production.dto.ProductionOperationDto;
import com.mfg.ims.production.dto.ProductionOrderDto;
import com.mfg.ims.production.dto.QuickCreateRequest;
import com.mfg.ims.production.dto.ShopFloorMaterialDto;
import com.mfg.ims.production.dto.UpdateRoutingRequest;
import com.mfg.ims.production.event.ProductionOrderCompletedEvent;
import com.mfg.ims.production.repository.ProductionOperationRepository;
import com.mfg.ims.production.repository.ProductionOrderMaterialRepository;
import com.mfg.ims.production.repository.ProductionOrderRepository;

/**
 * Production convenience features (13.9): complete-all, quick-create, routing patch, shop-floor materials.
 */
@Service
public class ProductionConvenienceService {

    private final ProductionOrderRepository orderRepo;
    private final ProductionOperationRepository operationRepo;
    private final ProductionOrderMaterialRepository materialRepo;
    private final MaterialRepository matRepo;
    private final BomRepository bomRepo;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProductionOrderService orderService;

    public ProductionConvenienceService(ProductionOrderRepository orderRepo,
                                        ProductionOperationRepository operationRepo,
                                        ProductionOrderMaterialRepository materialRepo,
                                        MaterialRepository matRepo,
                                        BomRepository bomRepo,
                                        AuditService auditService,
                                        ApplicationEventPublisher eventPublisher,
                                        ProductionOrderService orderService) {
        this.orderRepo = orderRepo;
        this.operationRepo = operationRepo;
        this.materialRepo = materialRepo;
        this.matRepo = matRepo;
        this.bomRepo = bomRepo;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.orderService = orderService;
    }

    /** Sets all PENDING/IN_PROGRESS operations to COMPLETED and moves the order to COMPLETED. */
    @Transactional
    public ProductionOrderDto completeAll(Long orderId) {
        ProductionOrder order = requireOrder(orderId);
        if (!List.of("RELEASED", "IN_PROGRESS").contains(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Order must be RELEASED or IN_PROGRESS to use complete-all, current: " + order.getStatus());
        }

        List<ProductionOperation> ops = operationRepo.findByProductionOrderIdOrderBySequenceNo(orderId);
        boolean allAlreadyCompleted = ops.stream().allMatch(op -> "COMPLETED".equals(op.getStatus()));
        if (allAlreadyCompleted) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "All operations are already COMPLETED");
        }

        Instant now = Instant.now();
        for (ProductionOperation op : ops) {
            if ("COMPLETED".equals(op.getStatus()) || "CANCELLED".equals(op.getStatus())) continue;
            op.setGoodQty(op.getInputQty());
            op.setStatus("COMPLETED");
            op.setCompletedAt(now);
            if (op.getStartedAt() == null) op.setStartedAt(now);
            operationRepo.save(op);
        }

        order.setStatus("COMPLETED");
        order.setActualCompletionAt(now);
        order.setGoodQty(order.getPlannedQty());
        order = orderRepo.save(order);

        auditService.record("PROD_ORDER_COMPLETE_ALL", "ProductionOrder", String.valueOf(orderId),
                "RELEASED", "COMPLETED", null);
        eventPublisher.publishEvent(new ProductionOrderCompletedEvent(orderId));

        return orderService.findById(orderId);
    }

    /** Creates and optionally immediately completes a production order from a BOM. */
    @Transactional
    public ProductionOrderDto quickCreate(QuickCreateRequest req) {
        Bom bom = bomRepo.findById(req.bomId())
                .orElseThrow(() -> new NotFoundException("BOM not found: " + req.bomId()));

        var createReq = new com.mfg.ims.production.dto.CreateProductionOrderRequest(
                bom.getProductId(), req.bomId(), req.qty(),
                null, null, null, null, null, null, (short) 3, null);

        ProductionOrderDto draft = orderService.create(createReq);
        ProductionOrderDto approved = orderService.approve(draft.id());
        ProductionOrderDto released = orderService.release(approved.id());

        if (req.completeImmediately()) {
            return completeAll(released.id());
        }
        return released;
    }

    /** Updates operation routing metadata when all operations are still PENDING. */
    @Transactional
    public List<ProductionOperationDto> updateRouting(Long orderId, UpdateRoutingRequest req) {
        requireOrder(orderId);

        List<ProductionOperation> ops = operationRepo.findByProductionOrderIdOrderBySequenceNo(orderId);
        boolean anyStarted = ops.stream()
                .anyMatch(op -> !"PENDING".equals(op.getStatus()) && !"CANCELLED".equals(op.getStatus()));
        if (anyStarted) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot update routing — some operations have already started");
        }

        StringBuilder oldValues = new StringBuilder();
        StringBuilder newValues = new StringBuilder();

        for (UpdateRoutingRequest.OperationUpdate upd : req.operations()) {
            ProductionOperation op = operationRepo.findById(upd.id())
                    .filter(o -> o.getProductionOrderId().equals(orderId))
                    .orElseThrow(() -> new NotFoundException("Operation not found in this order: " + upd.id()));

            oldValues.append("op").append(op.getSequenceNo()).append(":plannedEnd=")
                    .append(op.getPlannedEnd()).append(";");

            if (upd.plannedDurationMins() != null && upd.plannedDurationMins() > 0) {
                Instant start = op.getPlannedStart() != null ? op.getPlannedStart() : Instant.now();
                op.setPlannedStart(start);
                op.setPlannedEnd(start.plusSeconds(upd.plannedDurationMins() * 60L));
            }

            newValues.append("op").append(op.getSequenceNo()).append(":dur=")
                    .append(upd.plannedDurationMins()).append(";");

            operationRepo.save(op);
        }

        auditService.record("PROD_ORDER_ROUTING_UPDATE", "ProductionOrder", String.valueOf(orderId),
                oldValues.toString(), newValues.toString(), null);

        return orderService.getOperations(orderId);
    }

    /** Returns shop-floor material view: planned vs issued vs remaining per material. */
    @Transactional(readOnly = true)
    public List<ShopFloorMaterialDto> shopFloorMaterials(Long orderId) {
        requireOrder(orderId);
        return materialRepo.findByProductionOrderId(orderId).stream()
                .map(pom -> {
                    Material mat = matRepo.findById(pom.getMaterialId()).orElse(null);
                    return new ShopFloorMaterialDto(
                            pom.getMaterialId(),
                            mat != null ? mat.getMaterialCode() : null,
                            mat != null ? mat.getName() : null,
                            pom.getUom(),
                            pom.getPlannedQty(),
                            pom.getIssuedQty(),
                            pom.getRemainingQty());
                })
                .toList();
    }

    private ProductionOrder requireOrder(Long id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Production order not found: " + id));
    }
}
