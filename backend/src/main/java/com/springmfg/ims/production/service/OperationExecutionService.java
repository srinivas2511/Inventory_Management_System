package com.springmfg.ims.production.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.inventory.domain.FgBatch;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.repository.FgBatchRepository;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.production.domain.ProductionOperation;
import com.springmfg.ims.production.domain.ProductionOrder;
import com.springmfg.ims.production.domain.ProductionOutput;
import com.springmfg.ims.production.dto.ProductionOperationDto;
import com.springmfg.ims.production.dto.RecordOutputRequest;
import com.springmfg.ims.production.event.FinalInspectionRequiredEvent;
import com.springmfg.ims.production.event.OperationCompletedEvent;
import com.springmfg.ims.production.event.OperationStartedEvent;
import com.springmfg.ims.production.repository.ProductionOperationRepository;
import com.springmfg.ims.production.repository.ProductionOrderRepository;
import com.springmfg.ims.production.repository.ProductionOutputRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.warehouse.domain.WarehouseLocation;
import com.springmfg.ims.warehouse.repository.WarehouseLocationRepository;
import com.springmfg.ims.masterdata.operation.OperationRepository;
import com.springmfg.ims.masterdata.machine.MachineRepository;

@Service
public class OperationExecutionService {

    private final ProductionOperationRepository opRepo;
    private final ProductionOrderRepository orderRepo;
    private final ProductionOutputRepository outputRepo;
    private final FgBatchRepository fgBatchRepo;
    private final InventoryPostingService postingService;
    private final WarehouseLocationRepository locationRepo;
    private final DocumentNumberService numberService;
    private final SystemSettingService settingService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProductionOrderService orderService;
    private final OperationRepository opMasterRepo;
    private final MachineRepository machineRepo;
    private final ProductRepository productRepo;
    private final FinishedGoodsCostService fgCostService;
    private final OrderMaterialService materialService;

    public OperationExecutionService(
            ProductionOperationRepository opRepo,
            ProductionOrderRepository orderRepo,
            ProductionOutputRepository outputRepo,
            FgBatchRepository fgBatchRepo,
            InventoryPostingService postingService,
            WarehouseLocationRepository locationRepo,
            DocumentNumberService numberService,
            SystemSettingService settingService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,
            ProductionOrderService orderService,
            OperationRepository opMasterRepo,
            MachineRepository machineRepo,
            ProductRepository productRepo,
            FinishedGoodsCostService fgCostService,
            OrderMaterialService materialService) {
        this.opRepo = opRepo;
        this.orderRepo = orderRepo;
        this.outputRepo = outputRepo;
        this.fgBatchRepo = fgBatchRepo;
        this.postingService = postingService;
        this.locationRepo = locationRepo;
        this.numberService = numberService;
        this.settingService = settingService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.orderService = orderService;
        this.opMasterRepo = opMasterRepo;
        this.machineRepo = machineRepo;
        this.productRepo = productRepo;
        this.fgCostService = fgCostService;
        this.materialService = materialService;
    }

    @Transactional
    public ProductionOperationDto startOperation(Long operationId, Long machineId) {
        ProductionOperation op = findOp(operationId);
        if (!"PENDING".equals(op.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Operation must be PENDING to start, current: " + op.getStatus());
        }
        op.setStatus("IN_PROGRESS");
        op.setStartedAt(Instant.now());
        if (machineId != null) op.setMachineId(machineId);
        opRepo.save(op);
        orderService.moveToInProgressIfNeeded(op.getProductionOrderId());
        auditService.record("OP_START", "ProductionOperation", String.valueOf(operationId), "PENDING", "IN_PROGRESS", null);
        eventPublisher.publishEvent(new OperationStartedEvent(operationId, op.getProductionOrderId(),
                operatorOf(op), op.getStartedAt()));
        return toDto(op);
    }

    @Transactional
    public ProductionOperationDto pauseOperation(Long operationId) {
        ProductionOperation op = findOp(operationId);
        if (!"IN_PROGRESS".equals(op.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Operation must be IN_PROGRESS to pause, current: " + op.getStatus());
        }
        op.setStatus("PAUSED");
        opRepo.save(op);
        auditService.record("OP_PAUSE", "ProductionOperation", String.valueOf(operationId), "IN_PROGRESS", "PAUSED", null);
        return toDto(op);
    }

    @Transactional
    public ProductionOperationDto resumeOperation(Long operationId) {
        ProductionOperation op = findOp(operationId);
        if (!"PAUSED".equals(op.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Operation must be PAUSED to resume, current: " + op.getStatus());
        }
        op.setStatus("IN_PROGRESS");
        opRepo.save(op);
        auditService.record("OP_RESUME", "ProductionOperation", String.valueOf(operationId), "PAUSED", "IN_PROGRESS", null);
        return toDto(op);
    }

    @Transactional
    public ProductionOperationDto completeOperation(Long operationId) {
        ProductionOperation op = findOp(operationId);
        if (!"IN_PROGRESS".equals(op.getStatus()) && !"PAUSED".equals(op.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Operation cannot be completed in status: " + op.getStatus());
        }
        String previousStatus = op.getStatus();
        op.setStatus("COMPLETED");
        op.setCompletedAt(Instant.now());
        opRepo.save(op);
        auditService.record("OP_COMPLETE", "ProductionOperation", String.valueOf(operationId),
                previousStatus, "COMPLETED", null);
        eventPublisher.publishEvent(new OperationCompletedEvent(operationId, op.getProductionOrderId(),
                operatorOf(op), op.getCompletedAt()));

        checkAndCompleteOrder(op.getProductionOrderId(), op);
        return toDto(op);
    }

    @Transactional
    public ProductionOperationDto recordOutput(RecordOutputRequest req) {
        ProductionOperation op = findOp(req.productionOperationId());
        if (!"IN_PROGRESS".equals(op.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Operation must be IN_PROGRESS to record output, current: " + op.getStatus());
        }

        BigDecimal rejQty = req.rejectedQty() != null ? req.rejectedQty() : BigDecimal.ZERO;
        BigDecimal scrQty = req.scrapQty() != null ? req.scrapQty() : BigDecimal.ZERO;
        BigDecimal totalNew = req.goodQty().add(rejQty).add(scrQty);

        // Validate against remaining input
        BigDecimal totalRecordedSoFar = op.getGoodQty().add(op.getRejectedQty()).add(op.getScrapQty());
        BigDecimal remaining = op.getInputQty().subtract(totalRecordedSoFar);
        if (totalNew.compareTo(remaining) > 0) {
            throw new BusinessRuleException(ErrorCode.OPERATION_QTY_EXCEEDS_INPUT,
                    "Output exceeds remaining input. Remaining: " + remaining + ", attempted: " + totalNew);
        }

        // Entry no
        int entryNo = opRepo.findMaxEntryNo(op.getId()) + 1;

        ProductionOutput output = new ProductionOutput();
        output.setProductionOperationId(op.getId());
        output.setProductionOrderId(op.getProductionOrderId());
        output.setEntryNo(entryNo);
        output.setInputQty(totalNew);
        output.setGoodQty(req.goodQty());
        output.setRejectedQty(rejQty);
        output.setScrapQty(scrQty);
        output.setRejectionReasonId(req.rejectionReasonId());
        output.setOperatorId(currentUserId());
        output.setMachineId(req.machineId());
        output.setStartTime(req.startTime());
        output.setEndTime(req.endTime());
        output.setShift(req.shift());
        output.setRemarks(req.remarks());
        outputRepo.save(output);

        // Update operation totals
        op.setGoodQty(op.getGoodQty().add(req.goodQty()));
        op.setRejectedQty(op.getRejectedQty().add(rejQty));
        op.setScrapQty(op.getScrapQty().add(scrQty));
        opRepo.save(op);

        // Cascade good qty to next operation input
        List<ProductionOperation> allOps = opRepo.findByProductionOrderIdOrderBySequenceNo(op.getProductionOrderId());
        int idx = -1;
        for (int i = 0; i < allOps.size(); i++) {
            if (allOps.get(i).getId().equals(op.getId())) { idx = i; break; }
        }
        if (idx >= 0 && idx < allOps.size() - 1) {
            ProductionOperation next = allOps.get(idx + 1);
            next.setInputQty(next.getInputQty().add(req.goodQty()));
            opRepo.save(next);
        }

        // Move order to IN_PROGRESS
        orderService.moveToInProgressIfNeeded(op.getProductionOrderId());

        // Scrap posting
        if (scrQty.compareTo(BigDecimal.ZERO) > 0) {
            List<WarehouseLocation> scrapLocs = locationRepo.findByWarehouseIdAndActiveTrue(
                    findDefaultWarehouseId(op.getProductionOrderId()))
                    .stream().filter(l -> "SCRAP".equals(l.getLocationType())).toList();
            if (!scrapLocs.isEmpty()) {
                Long scrapProductId = getProductId(op.getProductionOrderId());
                BigDecimal scrapCost = productRepo.findById(scrapProductId)
                        .map(Product::getStandardCost).orElse(null);
                postingService.post(new PostingCommand(
                        "PRODUCTION_REJECTION", "PRODUCT",
                        null, scrapProductId, null, null,
                        scrapLocs.get(0).getWarehouse().getId(), scrapLocs.get(0).getId(), "SCRAP",
                        scrQty, null, scrapCost,
                        "PRODUCTION_OUTPUT", output.getId(), null, req.remarks()));
            }
        }

        // Backflush (if enabled): the first operation's output consumes the material it used. The first operation is the
        // one with the lowest sequence number (routing steps are numbered 10, 20, 30), and completion consumes whatever
        // is still outstanding, so backflush only moves the consumption earlier.
        if (idx == 0 && settingService.getBoolean("production.auto_backflush", false)) {
            materialService.consumeForOutput(op.getProductionOrderId());
        }

        // Check if last op with all completed
        if (idx == allOps.size() - 1) {
            checkAndCompleteOrder(op.getProductionOrderId(), op);
        }

        return toDto(op);
    }

    @Transactional(readOnly = true)
    public List<ProductionOperationDto> getMyWork(Long operatorId) {
        return opRepo.findMyWork(operatorId).stream().map(this::toDto).toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void checkAndCompleteOrder(Long orderId, ProductionOperation lastOp) {
        List<ProductionOperation> all = opRepo.findByProductionOrderIdOrderBySequenceNo(orderId);
        boolean allComplete = all.stream().allMatch(o -> "COMPLETED".equals(o.getStatus()));
        if (!allComplete) return;

        ProductionOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        // The material the made pieces used leaves WIP before the output is costed: FG is received at what was consumed,
        // and whatever is left over is returned or written off when the order is closed.
        materialService.consumeForOutput(orderId);

        // Create FG batch
        String fgBatchNum = numberService.next("FG");
        FgBatch fgBatch = new FgBatch();
        fgBatch.setBatchNumber(fgBatchNum);
        Product product = productRepo.findById(order.getProductId())
                .orElseThrow(() -> new NotFoundException("Product not found: " + order.getProductId()));
        fgBatch.setProduct(product);
        fgBatch.setProductionOrderId(orderId);
        fgBatch.setProducedQty(lastOp.getGoodQty());
        fgBatch.setRemainingQty(lastOp.getGoodQty());
        fgBatch.setProducedOn(LocalDate.now());
        fgBatch.setQualityStatus("PRODUCTION_COMPLETED");
        // Received at standard (released estimate) or at the actual material cost still in WIP after scrap; the
        // ledger value of the receipt is what the bridge journals as Dr 1410 FG / Cr 1501 WIP.
        FinishedGoodsCostService.FgCost fgCost = fgCostService.resolve(orderId, order.getProductId(), lastOp.getGoodQty());
        fgBatch.setUnitCost(fgCost.unitCost());
        fgBatch = fgBatchRepo.save(fgBatch);

        // Post PRODUCTION_RECEIPT to FG Quality Pending location
        Long warehouseId = findDefaultWarehouseId(orderId);
        List<WarehouseLocation> fgqLocs = locationRepo.findByWarehouseIdAndActiveTrue(warehouseId)
                .stream().filter(l -> "QUALITY_PENDING".equals(l.getLocationType())).toList();

        if (!fgqLocs.isEmpty() && lastOp.getGoodQty().compareTo(BigDecimal.ZERO) > 0) {
            postingService.post(new PostingCommand(
                    "PRODUCTION_RECEIPT", "PRODUCT",
                    null, order.getProductId(), null, fgBatch.getId(),
                    warehouseId, fgqLocs.get(0).getId(), "QUALITY_PENDING",
                    lastOp.getGoodQty(), null, fgCost.unitCost(),
                    "PRODUCTION_ORDER", orderId, order.getOrderNumber(),
                    "FG cost basis " + fgCost.basis()));
        }

        // Update order
        order.setGoodQty(lastOp.getGoodQty());
        orderRepo.save(order);
        orderService.onAllOperationsComplete(orderId);

        eventPublisher.publishEvent(new FinalInspectionRequiredEvent(
                orderId, order.getProductId(), order.getOrderNumber(),
                order.getPlannedQty(), lastOp.getGoodQty(), fgBatch.getId()));
    }

    private Long findDefaultWarehouseId(Long orderId) {
        // Use the warehouse of the first issue, fallback to 1L (MAIN-WH)
        return 1L;
    }

    private Long getProductId(Long orderId) {
        return orderRepo.findById(orderId).map(ProductionOrder::getProductId).orElse(null);
    }

    /** The assigned operator, else the authenticated user doing the work; null for background callers. */
    private static Long operatorOf(ProductionOperation op) {
        if (op.getAssignedOperatorId() != null) return op.getAssignedOperatorId();
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CachedUser u ? u.id() : null;
    }

    private ProductionOperation findOp(Long id) {
        return opRepo.findById(id).orElseThrow(() -> new NotFoundException("Operation not found: " + id));
    }

    private ProductionOperationDto toDto(ProductionOperation op) {
        String opName = opMasterRepo.findById(op.getOperationId()).map(o -> o.getName()).orElse(null);
        String machineName = op.getMachineId() != null
                ? machineRepo.findById(op.getMachineId()).map(m -> m.getName()).orElse(null) : null;
        ProductionOrder owner = orderRepo.findById(op.getProductionOrderId()).orElse(null);
        return new ProductionOperationDto(
                op.getId(), op.getSequenceNo(), op.getOperationId(), opName,
                op.getMachineId(), machineName, op.getAssignedOperatorId(),
                op.isInspectionPoint(), op.getStatus(),
                op.getInputQty(), op.getGoodQty(), op.getRejectedQty(), op.getScrapQty(),
                op.getStartedAt(), op.getCompletedAt(),
                op.getScheduledStart(), op.getScheduledEnd(),
                op.getProductionOrderId(),
                owner != null ? owner.getOrderNumber() : null,
                owner != null ? owner.getProductId() : null,
                !opRepo.existsByProductionOrderIdAndSequenceNoGreaterThan(op.getProductionOrderId(), op.getSequenceNo()));
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return null;
    }
}
