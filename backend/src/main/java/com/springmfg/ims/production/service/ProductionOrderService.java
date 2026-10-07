package com.springmfg.ims.production.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.engineering.bom.Bom;
import com.springmfg.ims.engineering.bom.BomItem;
import com.springmfg.ims.engineering.bom.BomRepository;
import com.springmfg.ims.engineering.routing.ProductRouting;
import com.springmfg.ims.engineering.routing.ProductRoutingRepository;
import com.springmfg.ims.masterdata.machine.MachineRepository;
import com.springmfg.ims.masterdata.operation.OperationRepository;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.production.domain.ProductionOperation;
import com.springmfg.ims.production.domain.ProductionOrder;
import com.springmfg.ims.production.domain.ProductionOrderMaterial;
import com.springmfg.ims.production.dto.CreateProductionOrderRequest;
import com.springmfg.ims.production.dto.ProductionOperationDto;
import com.springmfg.ims.production.dto.ProductionOrderDto;
import com.springmfg.ims.production.dto.ProductionOrderMaterialDto;
import com.springmfg.ims.production.dto.WipDto;
import com.springmfg.ims.production.event.ProductionOrderClosedEvent;
import com.springmfg.ims.production.repository.ProductionOperationRepository;
import com.springmfg.ims.production.repository.ProductionOrderMaterialRepository;
import com.springmfg.ims.production.repository.ProductionOrderRepository;

@Service
public class ProductionOrderService {

    private final ProductionOrderRepository orderRepo;
    private final ProductionOrderMaterialRepository materialRepo;
    private final ProductionOperationRepository operationRepo;
    private final BomRepository bomRepo;
    private final ProductRoutingRepository routingRepo;
    private final ProductRepository productRepo;
    private final MaterialRepository matRepo;
    private final OperationRepository opMasterRepo;
    private final MachineRepository machineRepo;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderMaterialService orderMaterialService;
    private final OperatorDirectory operatorDirectory;

    public ProductionOrderService(
            ProductionOrderRepository orderRepo,
            ProductionOrderMaterialRepository materialRepo,
            ProductionOperationRepository operationRepo,
            BomRepository bomRepo,
            ProductRoutingRepository routingRepo,
            ProductRepository productRepo,
            MaterialRepository matRepo,
            OperationRepository opMasterRepo,
            MachineRepository machineRepo,
            DocumentNumberService numberService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,
            OrderMaterialService orderMaterialService,
            OperatorDirectory operatorDirectory) {
        this.orderRepo = orderRepo;
        this.materialRepo = materialRepo;
        this.operationRepo = operationRepo;
        this.bomRepo = bomRepo;
        this.routingRepo = routingRepo;
        this.productRepo = productRepo;
        this.matRepo = matRepo;
        this.opMasterRepo = opMasterRepo;
        this.machineRepo = machineRepo;
        this.numberService = numberService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.orderMaterialService = orderMaterialService;
        this.operatorDirectory = operatorDirectory;
    }

    @Transactional
    public ProductionOrderDto create(CreateProductionOrderRequest req) {
        bomRepo.findById(req.bomId()).orElseThrow(() -> new NotFoundException("BOM not found: " + req.bomId()));
        productRepo.findById(req.productId()).orElseThrow(() -> new NotFoundException("Product not found: " + req.productId()));

        ProductionOrder order = new ProductionOrder();
        order.setOrderNumber(numberService.next("PO"));
        order.setProductId(req.productId());
        order.setBomId(req.bomId());
        order.setPlannedQty(req.plannedQty());
        order.setStartDate(req.startDate());
        order.setExpectedCompletionDate(req.expectedCompletionDate());
        order.setProductionLine(req.productionLine());
        order.setPrimaryMachineId(req.primaryMachineId());
        order.setSupervisorId(req.supervisorId());
        order.setSalesOrderId(req.salesOrderId());
        if (req.priority() != null) order.setPriority(req.priority());
        order.setRemarks(req.remarks());
        order.setStatus("DRAFT");
        order = orderRepo.save(order);

        auditService.record("PROD_ORDER_CREATE", "ProductionOrder", String.valueOf(order.getId()),
                null, order.getOrderNumber(), null);
        return toDto(order);
    }

    @Transactional
    public ProductionOrderDto approve(Long orderId) {
        ProductionOrder order = findOrder(orderId);
        assertTransition(order, "DRAFT", "APPROVED");
        order.setStatus("APPROVED");
        order = orderRepo.save(order);
        auditService.record("PROD_ORDER_APPROVE", "ProductionOrder", String.valueOf(orderId), "DRAFT", "APPROVED", null);
        return toDto(order);
    }

    @Transactional
    public ProductionOrderDto release(Long orderId) {
        ProductionOrder order = findOrder(orderId);
        assertTransition(order, "APPROVED", "RELEASED");

        // BOM must be ACTIVE at release time
        Long bomId = order.getBomId();
        Bom bom = bomRepo.findById(bomId)
                .orElseThrow(() -> new NotFoundException("BOM not found: " + bomId));
        if (!"ACTIVE".equals(bom.getStatus())) {
            throw new BusinessRuleException(ErrorCode.BOM_NOT_ACTIVE,
                    "BOM " + bom.getBomNumber() + " is not ACTIVE, current status: " + bom.getStatus());
        }

        // Snapshot BOM materials
        for (BomItem item : bom.getItems()) {
            if (!"MATERIAL".equals(item.getItemKind())) continue;
            BigDecimal plannedQty = item.getRequiredQty()
                    .multiply(order.getPlannedQty())
                    .divide(bom.getBaseQuantity(), 3, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.ONE.add(item.getScrapPct().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(3, RoundingMode.HALF_UP);

            Material mat = matRepo.findById(item.getMaterialId())
                    .orElseThrow(() -> new NotFoundException("Material not found: " + item.getMaterialId()));

            ProductionOrderMaterial pom = new ProductionOrderMaterial();
            pom.setProductionOrderId(order.getId());
            pom.setMaterialId(item.getMaterialId());
            pom.setPlannedQty(plannedQty);
            pom.setScrapPctApplied(item.getScrapPct());
            pom.setUom(item.getUom() != null ? item.getUom() : mat.getUom());
            materialRepo.save(pom);
        }

        // Snapshot routing
        List<ProductRouting> routing = routingRepo.findByProductIdAndActiveTrue(order.getProductId());
        // DESIGN 4.7: the first operation can process the whole planned quantity; every later operation receives
        // the previous operation's good output (OperationExecutionService.recordOutput cascades it).
        int firstSequence = routing.stream().mapToInt(ProductRouting::getSequenceNo).min().orElse(0);
        for (ProductRouting step : routing) {
            ProductionOperation op = new ProductionOperation();
            op.setProductionOrderId(order.getId());
            op.setSequenceNo(step.getSequenceNo());
            op.setOperationId(step.getOperationId());
            op.setMachineId(step.getMachineId());
            op.setInspectionPoint(step.isInspectionPoint());
            op.setStatus("PENDING");
            if (step.getSequenceNo() == firstSequence) {
                op.setInputQty(order.getPlannedQty());
            }
            operationRepo.save(op);
        }

        order.setStatus("RELEASED");
        order.setReleasedBy(currentUserId());
        order.setReleasedAt(Instant.now());
        order = orderRepo.save(order);

        auditService.record("PROD_ORDER_RELEASE", "ProductionOrder", String.valueOf(orderId), "APPROVED", "RELEASED", null);
        return toDto(order);
    }

    @Transactional
    public ProductionOrderDto cancel(Long orderId, String reason) {
        ProductionOrder order = findOrder(orderId);
        if (!List.of("DRAFT", "APPROVED", "RELEASED").contains(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot cancel order in status: " + order.getStatus());
        }
        order.setStatus("CANCELLED");
        order = orderRepo.save(order);
        auditService.record("PROD_ORDER_CANCEL", "ProductionOrder", String.valueOf(orderId),
                order.getStatus(), "CANCELLED", reason);
        return toDto(order);
    }

    /**
     * Closes a COMPLETED order (DESIGN 4.7). Every issued kilo must be consumed, returned or written off first: the
     * material the made pieces used is consumed here if that has not happened yet, and anything still left over has to
     * be returned beforehand ({@link OrderMaterialService#returnMaterial}) or is written off as scrap when a reason is
     * given. The variance journal is posted after this transaction commits, once the closed order's cost is final.
     */
    @Transactional
    public ProductionOrderDto close(Long orderId, String writeOffReason) {
        ProductionOrder order = findOrder(orderId);
        assertTransition(order, "COMPLETED", "CLOSED");

        orderMaterialService.consumeForOutput(orderId);
        List<OrderMaterialService.OpenMaterial> unreconciled = orderMaterialService.openMaterials(orderId);
        if (!unreconciled.isEmpty()) {
            if (writeOffReason == null || writeOffReason.isBlank()) {
                String list = unreconciled.stream()
                        .map(m -> m.materialCode() + " (remaining: " + m.quantity().toPlainString() + " " + m.uom() + ")")
                        .collect(java.util.stream.Collectors.joining(", "));
                throw new BusinessRuleException(ErrorCode.ORDER_LOCKED,
                        "Cannot close order — unreturned materials: " + list
                                + ". Return them, or close with a write-off reason.");
            }
            orderMaterialService.writeOffRemaining(orderId, writeOffReason);
        }

        order.setStatus("CLOSED");
        order.setClosedBy(currentUserId());
        order.setClosedAt(Instant.now());
        order = orderRepo.save(order);

        auditService.record("PROD_ORDER_CLOSE", "ProductionOrder", String.valueOf(orderId), "COMPLETED", "CLOSED", null);
        eventPublisher.publishEvent(new ProductionOrderClosedEvent(orderId));
        return toDto(order);
    }

    @Transactional
    public ProductionOrderDto reopen(Long orderId) {
        ProductionOrder order = findOrder(orderId);
        if (!"COMPLETED".equals(order.getStatus()) && !"CLOSED".equals(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Can only reopen COMPLETED or CLOSED orders, current: " + order.getStatus());
        }
        String old = order.getStatus();
        order.setStatus("RELEASED");
        order.setClosedBy(null);
        order.setClosedAt(null);
        order = orderRepo.save(order);
        auditService.record("PROD_ORDER_REOPEN", "ProductionOrder", String.valueOf(orderId), old, "RELEASED", null);
        return toDto(order);
    }

    @Transactional(readOnly = true)
    public ProductionOrderDto findById(Long orderId) {
        return toDto(findOrder(orderId));
    }

    @Transactional(readOnly = true)
    public Page<ProductionOrderDto> search(String status, Long productId, Pageable pageable) {
        return orderRepo.search(status, productId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<ProductionOrderMaterialDto> getMaterials(Long orderId) {
        return materialRepo.findByProductionOrderId(orderId).stream()
                .map(pom -> {
                    Material mat = matRepo.findById(pom.getMaterialId()).orElse(null);
                    return new ProductionOrderMaterialDto(
                            pom.getId(), pom.getMaterialId(),
                            mat != null ? mat.getMaterialCode() : null,
                            mat != null ? mat.getName() : null,
                            pom.getPlannedQty(), pom.getIssuedQty(), pom.getConsumedQty(),
                            pom.getReturnedQty(), pom.getScrapQty(), pom.getRemainingQty(),
                            pom.getUom(), null, null);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductionOperationDto> getOperations(Long orderId) {
        ProductionOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Production order not found: " + orderId));
        List<ProductionOperation> operations = operationRepo.findByProductionOrderIdOrderBySequenceNo(orderId);
        Map<Long, String> operatorNames = operatorDirectory.names(operations.stream()
                .map(ProductionOperation::getAssignedOperatorId).filter(Objects::nonNull).distinct().toList());
        return operations.stream().map(op -> toOperationDto(op, order, operatorNames)).toList();
    }

    @Transactional(readOnly = true)
    public List<WipDto> getWip(Long orderId) {
        List<ProductionOperation> ops = operationRepo.findByProductionOrderIdOrderBySequenceNo(orderId);
        List<WipDto> wip = new ArrayList<>();
        for (int i = 1; i < ops.size(); i++) {
            ProductionOperation prev = ops.get(i - 1);
            ProductionOperation curr = ops.get(i);
            BigDecimal wipQty = prev.getGoodQty()
                    .subtract(curr.getGoodQty())
                    .subtract(curr.getRejectedQty())
                    .subtract(curr.getScrapQty());
            String opName = opMasterRepo.findById(curr.getOperationId()).map(o -> o.getName()).orElse(null);
            wip.add(new WipDto(curr.getOperationId(), opName, curr.getSequenceNo(), wipQty));
        }
        return wip;
    }

    // Called by OperationExecutionService when last op completes
    @Transactional
    public void onAllOperationsComplete(Long orderId) {
        ProductionOrder order = findOrder(orderId);
        order.setStatus("COMPLETED");
        order.setActualCompletionAt(Instant.now());
        orderRepo.save(order);
        auditService.record("PROD_ORDER_COMPLETE", "ProductionOrder", String.valueOf(orderId),
                "IN_PROGRESS", "COMPLETED", null);
    }

    void moveToInProgressIfNeeded(Long orderId) {
        ProductionOrder order = findOrder(orderId);
        if ("RELEASED".equals(order.getStatus())) {
            order.setStatus("IN_PROGRESS");
            order.setActualStartAt(Instant.now());
            orderRepo.save(order);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ProductionOrder findOrder(Long orderId) {
        return orderRepo.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Production order not found: " + orderId));
    }

    private void assertTransition(ProductionOrder order, String expected, String target) {
        if (!expected.equals(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Order " + order.getOrderNumber() + " must be " + expected + " to move to " + target
                            + ", current: " + order.getStatus());
        }
    }

    ProductionOrderDto toDto(ProductionOrder order) {
        Product product = productRepo.findById(order.getProductId()).orElse(null);
        Bom bom = bomRepo.findById(order.getBomId()).orElse(null);
        List<ProductionOrderMaterialDto> mats = materialRepo.findByProductionOrderId(order.getId()).stream()
                .map(pom -> {
                    Material mat = matRepo.findById(pom.getMaterialId()).orElse(null);
                    return new ProductionOrderMaterialDto(
                            pom.getId(), pom.getMaterialId(),
                            mat != null ? mat.getMaterialCode() : null,
                            mat != null ? mat.getName() : null,
                            pom.getPlannedQty(), pom.getIssuedQty(), pom.getConsumedQty(),
                            pom.getReturnedQty(), pom.getScrapQty(), pom.getRemainingQty(),
                            pom.getUom(), null, null);
                })
                .toList();
        List<ProductionOperation> operations = operationRepo.findByProductionOrderIdOrderBySequenceNo(order.getId());
        Map<Long, String> operatorNames = operatorDirectory.names(operations.stream()
                .map(ProductionOperation::getAssignedOperatorId).filter(Objects::nonNull).distinct().toList());
        List<ProductionOperationDto> ops = operations.stream()
                .map(op -> toOperationDto(op, order, operatorNames))
                .toList();

        return new ProductionOrderDto(
                order.getId(), order.getOrderNumber(),
                order.getProductId(),
                product != null ? product.getProductCode() : null,
                product != null ? product.getName() : null,
                order.getBomId(),
                bom != null ? bom.getBomNumber() : null,
                order.getPlannedQty(), order.getGoodQty(), order.getRejectedQty(), order.getScrapQty(),
                order.getStartDate(), order.getExpectedCompletionDate(),
                order.getActualStartAt(), order.getActualCompletionAt(),
                order.getProductionLine(), order.getSupervisorId(), order.getStatus(),
                order.getPriority(),
                order.getReleasedAt(), order.getClosedAt(), order.getRemarks(),
                mats, ops, allowedActions(order.getStatus()));
    }

    private ProductionOperationDto toOperationDto(ProductionOperation op, ProductionOrder order,
                                                  Map<Long, String> operatorNames) {
        String opName = opMasterRepo.findById(op.getOperationId()).map(o -> o.getName()).orElse(null);
        String machineName = op.getMachineId() != null
                ? machineRepo.findById(op.getMachineId()).map(m -> m.getName()).orElse(null) : null;
        return new ProductionOperationDto(
                op.getId(), op.getSequenceNo(), op.getOperationId(), opName,
                op.getMachineId(), machineName, op.getAssignedOperatorId(),
                op.getAssignedOperatorId() == null ? null : operatorNames.get(op.getAssignedOperatorId()),
                op.isInspectionPoint(), op.getStatus(),
                op.getInputQty(), op.getGoodQty(), op.getRejectedQty(), op.getScrapQty(),
                op.getStartedAt(), op.getCompletedAt(),
                op.getScheduledStart(), op.getScheduledEnd(),
                order.getId(), order.getOrderNumber(), order.getProductId(),
                !operationRepo.existsByProductionOrderIdAndSequenceNoGreaterThan(order.getId(), op.getSequenceNo()));
    }

    private List<String> allowedActions(String status) {
        return switch (status) {
            case "DRAFT" -> List.of("approve", "cancel");
            case "APPROVED" -> List.of("release", "cancel");
            case "RELEASED" -> List.of("cancel");
            case "IN_PROGRESS" -> List.of();
            case "COMPLETED" -> List.of("close");
            case "CLOSED" -> List.of("reopen");
            default -> List.of();
        };
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return null;
    }
}
