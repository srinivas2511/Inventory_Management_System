package com.springmfg.ims.quality.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.inventory.domain.FgBatch;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.dto.TransferCommand;
import com.springmfg.ims.inventory.repository.FgBatchRepository;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.production.domain.ProductionOrder;
import com.springmfg.ims.production.event.FinalInspectionRequiredEvent;
import com.springmfg.ims.production.repository.ProductionOperationRepository;
import com.springmfg.ims.production.repository.ProductionOrderRepository;
import com.springmfg.ims.quality.domain.InspectionPlanParameter;
import com.springmfg.ims.quality.domain.QualityInspection;
import com.springmfg.ims.quality.domain.QualityInspectionItem;
import com.springmfg.ims.quality.domain.Rejection;
import com.springmfg.ims.quality.dto.ApproveFgHoldRequest;
import com.springmfg.ims.quality.dto.CreateFinalInspectionRequest;
import com.springmfg.ims.quality.dto.CreateInProcessInspectionRequest;
import com.springmfg.ims.quality.dto.FgBatchDto;
import com.springmfg.ims.quality.dto.FinalInspectionDecisionRequest;
import com.springmfg.ims.quality.dto.QualityInspectionDto;
import com.springmfg.ims.quality.repository.InspectionPlanRepository;
import com.springmfg.ims.quality.repository.QualityInspectionItemRepository;
import com.springmfg.ims.quality.repository.QualityInspectionRepository;
import com.springmfg.ims.quality.repository.RejectionRepository;
import com.springmfg.ims.warehouse.domain.WarehouseLocation;
import com.springmfg.ims.warehouse.repository.WarehouseLocationRepository;

@Service
public class FinalInspectionService {

    private final QualityInspectionRepository inspectionRepo;
    private final QualityInspectionItemRepository itemRepo;
    private final InspectionPlanRepository planRepo;
    private final FgBatchRepository fgBatchRepo;
    private final RejectionRepository rejectionRepo;
    private final InventoryPostingService postingService;
    private final WarehouseLocationRepository locationRepo;
    private final ProductionOperationRepository opRepo;
    private final ProductionOrderRepository orderRepo;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    private static final long DEFAULT_WAREHOUSE_ID = 1L;

    public FinalInspectionService(
            QualityInspectionRepository inspectionRepo,
            QualityInspectionItemRepository itemRepo,
            InspectionPlanRepository planRepo,
            FgBatchRepository fgBatchRepo,
            RejectionRepository rejectionRepo,
            InventoryPostingService postingService,
            WarehouseLocationRepository locationRepo,
            ProductionOperationRepository opRepo,
            ProductionOrderRepository orderRepo,
            DocumentNumberService numberService,
            AuditService auditService) {
        this.inspectionRepo = inspectionRepo;
        this.itemRepo = itemRepo;
        this.planRepo = planRepo;
        this.fgBatchRepo = fgBatchRepo;
        this.rejectionRepo = rejectionRepo;
        this.postingService = postingService;
        this.locationRepo = locationRepo;
        this.opRepo = opRepo;
        this.orderRepo = orderRepo;
        this.numberService = numberService;
        this.auditService = auditService;
    }

    // ── Task 5.1 — event-driven auto-create FINAL inspection ─────────────────

    @EventListener
    @Transactional
    public void onFinalInspectionRequired(FinalInspectionRequiredEvent event) {
        FgBatch batch = fgBatchRepo.findById(event.fgBatchId()).orElse(null);
        if (batch == null) return;
        // idempotent: a repeated event, or a manually created inspection, must not add a second open one
        if (inspectionRepo.findOpenFinalByFgBatch(event.fgBatchId()).isPresent()) return;

        batch.setQualityStatus("QUALITY_PENDING");
        fgBatchRepo.save(batch);

        QualityInspection insp = new QualityInspection();
        insp.setInspectionNumber(numberService.next("QI"));
        insp.setInspectionType("FINAL");
        insp.setStatus("PENDING");
        insp.setFgBatchId(event.fgBatchId());
        insp.setProductionOrderId(event.productionOrderId());
        insp.setInspectedQty(event.goodQty());
        insp = inspectionRepo.save(insp);

        final QualityInspection finalInsp = insp;
        planRepo.findByInspectionTypeAndProductIdAndActiveTrue("FINAL", event.productId())
                .ifPresent(plan -> prefillItems(finalInsp, plan.getParameters()));
    }

    // ── Task 5.2 — In-process inspection ─────────────────────────────────────

    @Transactional
    public QualityInspectionDto createInProcess(CreateInProcessInspectionRequest req) {
        var op = opRepo.findById(req.productionOperationId())
                .orElseThrow(() -> new NotFoundException("Operation not found: " + req.productionOperationId()));
        if (!op.isInspectionPoint()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Operation is not marked as an inspection point");
        }
        if (!"IN_PROGRESS".equals(op.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Operation must be IN_PROGRESS for in-process inspection");
        }

        Long productId = orderRepo.findById(op.getProductionOrderId())
                .map(ProductionOrder::getProductId).orElse(null);

        QualityInspection insp = new QualityInspection();
        insp.setInspectionNumber(numberService.next("QI"));
        insp.setInspectionType("IN_PROCESS");
        insp.setStatus("PENDING");
        insp.setProductionOperationId(req.productionOperationId());
        insp.setProductionOrderId(op.getProductionOrderId());
        insp.setInspectedQty(req.inspectedQty());
        insp = inspectionRepo.save(insp);

        if (productId != null) {
            final QualityInspection saved = insp;
            final Long pId = productId;
            planRepo.findByInspectionTypeAndProductIdAndActiveTrue("IN_PROCESS", pId)
                    .ifPresent(plan -> prefillItems(saved, plan.getParameters()));
        }

        return toDto(insp);
    }

    // ── Task 5.3 — Final inspection (manual create) ───────────────────────────

    @Transactional
    public QualityInspectionDto createFinal(CreateFinalInspectionRequest req) {
        FgBatch batch = fgBatchRepo.findById(req.fgBatchId())
                .orElseThrow(() -> new NotFoundException("FG batch not found: " + req.fgBatchId()));

        if (inspectionRepo.findOpenFinalByFgBatch(req.fgBatchId()).isPresent()) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "An open final inspection already exists for batch " + req.fgBatchId());
        }

        QualityInspection insp = new QualityInspection();
        insp.setInspectionNumber(numberService.next("QI"));
        insp.setInspectionType("FINAL");
        insp.setStatus("PENDING");
        insp.setFgBatchId(req.fgBatchId());
        insp.setProductionOrderId(batch.getProductionOrderId());
        insp.setInspectedQty(req.inspectedQty());
        if (req.sampleSize() != null) insp.setSampleSize(req.sampleSize());
        insp = inspectionRepo.save(insp);

        final QualityInspection saved = insp;
        planRepo.findByInspectionTypeAndProductIdAndActiveTrue("FINAL", batch.getProduct().getId())
                .ifPresent(plan -> prefillItems(saved, plan.getParameters()));

        return toDto(insp);
    }

    // ── Task 5.3 — Complete final inspection with ledger movements ────────────

    @Transactional
    public QualityInspectionDto completeFinalInspection(Long inspectionId, FinalInspectionDecisionRequest req) {
        QualityInspection insp = requireInspection(inspectionId);
        if (!"FINAL".equals(insp.getInspectionType())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION, "Not a FINAL inspection");
        }
        if (!"PENDING".equals(insp.getStatus()) && !"IN_PROGRESS".equals(insp.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Inspection must be PENDING or IN_PROGRESS, current: " + insp.getStatus());
        }
        if (insp.getFgBatchId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "No FG batch linked to this inspection");
        }

        Long fgBatchId0 = insp.getFgBatchId();
        FgBatch batch = fgBatchRepo.findById(fgBatchId0)
                .orElseThrow(() -> new NotFoundException("FG batch not found: " + fgBatchId0));
        if (!"QUALITY_PENDING".equals(batch.getQualityStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "FG batch is not in QUALITY_PENDING status");
        }

        BigDecimal approved = req.approvedQty() != null ? req.approvedQty() : BigDecimal.ZERO;
        BigDecimal rejected = req.rejectedQty() != null ? req.rejectedQty() : BigDecimal.ZERO;
        BigDecimal hold = req.holdQty() != null ? req.holdQty() : BigDecimal.ZERO;
        BigDecimal total = approved.add(rejected).add(hold);
        if (total.compareTo(insp.getInspectedQty()) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Sum of approved+rejected+hold (" + total + ") exceeds inspected qty (" + insp.getInspectedQty() + ")");
        }

        List<QualityInspectionItem> items = itemRepo.findByInspectionId(inspectionId);
        if (req.itemResults() != null) {
            for (FinalInspectionDecisionRequest.ItemResultEntry entry : req.itemResults()) {
                items.stream()
                        .filter(i -> i.getSeq() != null && i.getSeq() == entry.seq())
                        .findFirst()
                        .ifPresent(i -> {
                            i.setActualValue(entry.actualValue());
                            i.setActualText(entry.actualText());
                            if (entry.deviationNote() != null) i.setDeviationNote(entry.deviationNote());
                            i.computeResult();
                            itemRepo.save(i);
                        });
            }
            items = itemRepo.findByInspectionId(inspectionId);
        }

        boolean anyUnresolvedFail = items.stream()
                .filter(QualityInspectionItem::isMandatory)
                .filter(i -> "FAIL".equals(i.getResult()))
                .anyMatch(i -> i.getDeviationApprovedBy() == null);

        if (anyUnresolvedFail && approved.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessRuleException(ErrorCode.INSPECTION_INCOMPLETE,
                    "Mandatory parameters failed without Quality Manager deviation approval. Approve deviations first.");
        }

        List<WarehouseLocation> warehouseLocs = locationRepo.findByWarehouseIdAndActiveTrue(DEFAULT_WAREHOUSE_ID);
        WarehouseLocation fgqLoc = warehouseLocs.stream()
                .filter(l -> "QUALITY_PENDING".equals(l.getLocationType())).findFirst().orElse(null);
        WarehouseLocation fgLoc = warehouseLocs.stream()
                .filter(l -> "FINISHED".equals(l.getLocationType())).findFirst().orElse(null);
        WarehouseLocation scrapLoc = warehouseLocs.stream()
                .filter(l -> "SCRAP".equals(l.getLocationType())).findFirst().orElse(null);

        Long batchId = insp.getFgBatchId();
        Long productId = batch.getProduct().getId();
        String inspNumber = insp.getInspectionNumber();

        // Approved: move QUALITY_PENDING → FINISHED (AVAILABLE)
        if (approved.compareTo(BigDecimal.ZERO) > 0 && fgqLoc != null && fgLoc != null) {
            postingService.transfer(new TransferCommand(
                    "PRODUCT", null, productId, null, batchId,
                    DEFAULT_WAREHOUSE_ID, fgqLoc.getId(), "QUALITY_PENDING",
                    DEFAULT_WAREHOUSE_ID, fgLoc.getId(), "AVAILABLE",
                    approved, null,
                    "QUALITY_INSPECTION", inspectionId, inspNumber,
                    "FG approved in final inspection"));
        }

        // Rejected: written off the FG books and held as REJECTED stock in the scrap location (see writeOffRejectedFg).
        if (rejected.compareTo(BigDecimal.ZERO) > 0 && fgqLoc != null && scrapLoc != null) {
            writeOffRejectedFg(batch, fgqLoc, "QUALITY_PENDING", scrapLoc, rejected,
                    "QUALITY_INSPECTION", inspectionId, inspNumber);

            if (req.rejectionReasons() != null) {
                Long userId = currentUserId();
                for (FinalInspectionDecisionRequest.RejectionReasonLine line : req.rejectionReasons()) {
                    if (line.quantity() == null || line.quantity().compareTo(BigDecimal.ZERO) <= 0) continue;
                    Rejection r = new Rejection();
                    r.setInspectionId(inspectionId);
                    r.setRejectionReasonId(line.rejectionReasonId());
                    r.setItemType("PRODUCT");
                    r.setProductId(productId);
                    r.setBatchId(batchId);
                    r.setQuantity(line.quantity());
                    r.setDisposition("SCRAP");
                    r.setRecordedBy(userId != null ? userId : -1L);
                    r.setRecordedAt(Instant.now());
                    rejectionRepo.save(r);
                }
            }
        }

        // Hold: change stock status QUALITY_PENDING → HOLD at same location (status-only transfer)
        if (hold.compareTo(BigDecimal.ZERO) > 0 && fgqLoc != null) {
            postingService.transfer(new TransferCommand(
                    "PRODUCT", null, productId, null, batchId,
                    DEFAULT_WAREHOUSE_ID, fgqLoc.getId(), "QUALITY_PENDING",
                    DEFAULT_WAREHOUSE_ID, fgqLoc.getId(), "HOLD",
                    hold, null,
                    "QUALITY_INSPECTION", inspectionId, inspNumber,
                    "FG placed on hold"));
        }

        String newStatus;
        if (hold.compareTo(BigDecimal.ZERO) > 0) {
            newStatus = "HOLD";
        } else if (approved.compareTo(BigDecimal.ZERO) == 0) {
            newStatus = "REJECTED";
        } else if (rejected.compareTo(BigDecimal.ZERO) > 0) {
            newStatus = "PARTIALLY_APPROVED";
        } else {
            newStatus = "APPROVED";
        }
        batch.setApprovedQty(approved);
        batch.setRejectedQty(rejected);
        batch.setRemainingQty(approved);
        batch.setQualityStatus(newStatus);
        fgBatchRepo.save(batch);

        insp.setAcceptedQty(approved);
        insp.setRejectedQty(rejected);
        insp.setHoldQty(hold);
        insp.setInspectorId(currentUserId());
        insp.setInspectedAt(Instant.now());
        insp.setDecisionBy(currentUserId());
        insp.setDecisionAt(Instant.now());
        insp.setStatus(anyUnresolvedFail ? "REJECTED" : (hold.compareTo(BigDecimal.ZERO) > 0 ? "HOLD" : "APPROVED"));
        if (req.remarks() != null) insp.setRemarks(req.remarks());
        insp = inspectionRepo.save(insp);

        auditService.record("FINAL_INSPECTION_COMPLETED", "QUALITY_INSPECTION",
                String.valueOf(inspectionId), "PENDING", newStatus,
                "approved=" + approved + " rejected=" + rejected + " hold=" + hold);

        return toDto(insp);
    }

    // ── Task 5.5 — Deviation override ────────────────────────────────────────

    @Transactional
    public QualityInspectionDto recordDeviationOverride(Long inspectionId, int seq, String deviationNote) {
        QualityInspection insp = requireInspection(inspectionId);
        Long userId = currentUserId();

        QualityInspectionItem item = itemRepo.findByInspectionId(inspectionId).stream()
                .filter(i -> i.getSeq() != null && i.getSeq() == seq)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Inspection item seq " + seq + " not found"));

        item.setDeviationNote(deviationNote);
        item.setDeviationApprovedBy(userId);
        item.setDeviationApprovedAt(Instant.now());
        itemRepo.save(item);

        auditService.record("DEVIATION_OVERRIDE", "QUALITY_INSPECTION_ITEM",
                String.valueOf(item.getId()), null, deviationNote,
                "Override by Quality Manager on inspection " + inspectionId);

        return toDto(insp);
    }

    /**
     * Rejected finished goods leave the FG books at the cost the batch carries (SCRAP out-leg, bridge Dr 5202 Scrap
     * Loss / Cr 1410 FG Stock; the FG receipt already relieved WIP, so WIP is not touched again) and arrive in the
     * scrap location as REJECTED stock at zero value (PRODUCTION_REJECTION, GL-neutral for these references). REJECTED
     * stock is never AVAILABLE, so it cannot be reserved, dispatched or picked.
     */
    private void writeOffRejectedFg(FgBatch batch, WarehouseLocation from, String fromStatus,
                                    WarehouseLocation scrapLoc, BigDecimal qty,
                                    String refType, Long refId, String refNumber) {
        Long productId = batch.getProduct().getId();
        postingService.post(new PostingCommand(
                "SCRAP", "PRODUCT", null, productId, null, batch.getId(),
                DEFAULT_WAREHOUSE_ID, from.getId(), fromStatus,
                qty, null, batch.getUnitCost(),
                refType, refId, refNumber, "FG rejected: written off"));
        postingService.post(new PostingCommand(
                "PRODUCTION_REJECTION", "PRODUCT", null, productId, null, batch.getId(),
                DEFAULT_WAREHOUSE_ID, scrapLoc.getId(), "REJECTED",
                qty, null, BigDecimal.ZERO,
                refType, refId, refNumber, "FG rejected: held as rejected stock"));
    }

    // ── Task 5.3 — FG hold approval ──────────────────────────────────────────

    @Transactional
    public FgBatchDto approveHold(Long fgBatchId, ApproveFgHoldRequest req) {
        FgBatch batch = fgBatchRepo.findById(fgBatchId)
                .orElseThrow(() -> new NotFoundException("FG batch not found: " + fgBatchId));
        if (!"HOLD".equals(batch.getQualityStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "FG batch is not in HOLD status, current: " + batch.getQualityStatus());
        }

        BigDecimal approved = req.approvedQty() != null ? req.approvedQty() : BigDecimal.ZERO;
        BigDecimal rejected = req.rejectedQty() != null ? req.rejectedQty() : BigDecimal.ZERO;

        List<WarehouseLocation> locs = locationRepo.findByWarehouseIdAndActiveTrue(DEFAULT_WAREHOUSE_ID);
        WarehouseLocation fgqLoc = locs.stream().filter(l -> "QUALITY_PENDING".equals(l.getLocationType())).findFirst().orElse(null);
        WarehouseLocation fgLoc = locs.stream().filter(l -> "FINISHED".equals(l.getLocationType())).findFirst().orElse(null);
        WarehouseLocation scrapLoc = locs.stream().filter(l -> "SCRAP".equals(l.getLocationType())).findFirst().orElse(null);

        Long productId = batch.getProduct().getId();
        String batchNum = batch.getBatchNumber();

        if (approved.compareTo(BigDecimal.ZERO) > 0 && fgqLoc != null && fgLoc != null) {
            postingService.transfer(new TransferCommand(
                    "PRODUCT", null, productId, null, fgBatchId,
                    DEFAULT_WAREHOUSE_ID, fgqLoc.getId(), "HOLD",
                    DEFAULT_WAREHOUSE_ID, fgLoc.getId(), "AVAILABLE",
                    approved, null,
                    "FG_BATCH", fgBatchId, batchNum, req.reason()));
        }

        if (rejected.compareTo(BigDecimal.ZERO) > 0 && fgqLoc != null && scrapLoc != null) {
            writeOffRejectedFg(batch, fgqLoc, "HOLD", scrapLoc, rejected, "FG_BATCH", fgBatchId, batchNum);

            if (req.rejectionReasons() != null) {
                Long userId = currentUserId();
                for (var line : req.rejectionReasons()) {
                    if (line.quantity() == null || line.quantity().compareTo(BigDecimal.ZERO) <= 0) continue;
                    Rejection r = new Rejection();
                    r.setRejectionReasonId(line.rejectionReasonId());
                    r.setItemType("PRODUCT");
                    r.setProductId(productId);
                    r.setBatchId(fgBatchId);
                    r.setQuantity(line.quantity());
                    r.setDisposition("SCRAP");
                    r.setRecordedBy(userId != null ? userId : -1L);
                    r.setRecordedAt(Instant.now());
                    rejectionRepo.save(r);
                }
            }
        }

        batch.setApprovedQty(batch.getApprovedQty().add(approved));
        batch.setRejectedQty(batch.getRejectedQty().add(rejected));
        batch.setRemainingQty(batch.getRemainingQty().add(approved));
        batch.setQualityStatus(approved.compareTo(BigDecimal.ZERO) > 0 ? "PARTIALLY_APPROVED" : "REJECTED");
        fgBatchRepo.save(batch);

        auditService.record("FG_HOLD_RESOLVED", "FG_BATCH", String.valueOf(fgBatchId),
                "HOLD", batch.getQualityStatus(), req.reason());

        return toFgBatchDto(batch);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<FgBatchDto> searchFgBatches(String status, Long productId, Pageable pageable) {
        return fgBatchRepo.search(status, productId, pageable).map(this::toFgBatchDto);
    }

    @Transactional(readOnly = true)
    public FgBatchDto getFgBatch(Long id) {
        return toFgBatchDto(fgBatchRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("FG batch not found: " + id)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void prefillItems(QualityInspection insp, List<InspectionPlanParameter> params) {
        int seq = 1;
        for (InspectionPlanParameter param : params) {
            QualityInspectionItem item = new QualityInspectionItem();
            item.setInspection(insp);
            item.setSeq(seq++);
            item.setParameterName(param.getParameterName());
            item.setSpecification(param.getSpecification());
            item.setLowerLimit(param.getLowerLimit());
            item.setUpperLimit(param.getUpperLimit());
            item.setUnit(param.getUnit());
            item.setMandatory(param.isMandatory());
            itemRepo.save(item);
        }
    }

    private QualityInspection requireInspection(Long id) {
        return inspectionRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Quality inspection not found: " + id));
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.springmfg.ims.auth.service.UserPermissionCache.CachedUser u) {
                return u.id();
            }
        } catch (Exception ignored) {}
        return null;
    }

    QualityInspectionDto toDto(QualityInspection insp) {
        List<QualityInspectionItem> items = itemRepo.findByInspectionId(insp.getId());
        List<QualityInspectionDto.InspectionItemDto> itemDtos = items.stream()
                .map(i -> new QualityInspectionDto.InspectionItemDto(
                        i.getId(), i.getSeq(), i.getParameterName(), i.getSpecification(),
                        i.getLowerLimit(), i.getUpperLimit(), i.getActualValue(),
                        i.getActualText(), i.getUnit(), i.getResult(), i.getDeviationNote(),
                        i.isMandatory(), i.getDeviationApprovedBy(), i.getDeviationApprovedAt()))
                .toList();
        return new QualityInspectionDto(
                insp.getId(), insp.getInspectionNumber(), insp.getInspectionType(), insp.getStatus(),
                insp.getGoodsReceiptItemId(), null,
                insp.getMaterialBatchId(), null,
                insp.getMaterialId(), null,
                insp.getFgBatchId(), insp.getProductionOrderId(), insp.getProductionOperationId(),
                insp.getInspectedQty(), insp.getSampleSize(),
                insp.getAcceptedQty(), insp.getRejectedQty(), insp.getHoldQty(),
                insp.getInspectorId(), insp.getInspectedAt(), insp.getDecisionAt(),
                insp.getRemarks(), itemDtos);
    }

    FgBatchDto toFgBatchDto(FgBatch b) {
        Long pendingInspId = inspectionRepo.findOpenFinalByFgBatch(b.getId())
                .map(QualityInspection::getId).orElse(null);
        return new FgBatchDto(
                b.getId(), b.getBatchNumber(),
                b.getProduct().getId(), b.getProduct().getProductCode(), b.getProduct().getName(),
                b.getProductionOrderId(),
                b.getProducedQty(), b.getApprovedQty(), b.getRejectedQty(), b.getRemainingQty(),
                b.getProducedOn(), b.getQualityStatus(), b.getUnitCost(),
                pendingInspId);
    }
}
