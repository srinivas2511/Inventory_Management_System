package com.springmfg.ims.quality.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.event.InspectionResultsSubmittedEvent;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.inventory.domain.MaterialBatch;
import com.springmfg.ims.inventory.dto.TransferCommand;
import com.springmfg.ims.inventory.repository.MaterialBatchRepository;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.inventory.service.StockLocationQueryService;
import com.springmfg.ims.inventory.service.StockLocationQueryService.LocationStock;
import com.springmfg.ims.procurement.event.GoodsReceivedForInspectionEvent;
import com.springmfg.ims.procurement.repository.PurchaseOrderItemRepository;
import com.springmfg.ims.quality.domain.InspectionPlan;
import com.springmfg.ims.quality.domain.InspectionPlanParameter;
import com.springmfg.ims.quality.domain.QualityInspection;
import com.springmfg.ims.quality.domain.QualityInspectionItem;
import com.springmfg.ims.quality.dto.InspectionResultRequest;
import com.springmfg.ims.quality.dto.QualityInspectionDto;
import com.springmfg.ims.quality.repository.InspectionPlanRepository;
import com.springmfg.ims.quality.repository.QualityInspectionItemRepository;
import com.springmfg.ims.quality.repository.QualityInspectionRepository;

@Service
public class InspectionService {

    private final QualityInspectionRepository inspectionRepo;
    private final QualityInspectionItemRepository inspectionItemRepo;
    private final InspectionPlanRepository planRepo;
    private final MaterialBatchRepository batchRepo;
    private final PurchaseOrderItemRepository poItemRepo;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final StockLocationQueryService stockQuery;
    private final InventoryPostingService postingService;

    public InspectionService(
            QualityInspectionRepository inspectionRepo,
            QualityInspectionItemRepository inspectionItemRepo,
            InspectionPlanRepository planRepo,
            MaterialBatchRepository batchRepo,
            PurchaseOrderItemRepository poItemRepo,
            DocumentNumberService numberService,
            AuditService auditService,
            ApplicationEventPublisher events,
            StockLocationQueryService stockQuery,
            InventoryPostingService postingService) {
        this.inspectionRepo = inspectionRepo;
        this.inspectionItemRepo = inspectionItemRepo;
        this.planRepo = planRepo;
        this.batchRepo = batchRepo;
        this.poItemRepo = poItemRepo;
        this.numberService = numberService;
        this.auditService = auditService;
        this.events = events;
        this.stockQuery = stockQuery;
        this.postingService = postingService;
    }

    /** Triggered by GoodsReceiptService after each GR item is saved. */
    @EventListener
    @Transactional
    public void createIncoming(GoodsReceivedForInspectionEvent event) {
        if (!event.inspectionRequired()) return;

        QualityInspection insp = new QualityInspection();
        insp.setInspectionNumber(numberService.next("QI"));
        insp.setInspectionType("INCOMING");
        insp.setStatus("PENDING");
        insp.setGoodsReceiptItemId(event.goodsReceiptItemId());
        insp.setMaterialBatchId(event.materialBatchId());
        insp.setMaterialId(event.materialId());

        MaterialBatch batch = batchRepo.findById(event.materialBatchId()).orElse(null);
        insp.setInspectedQty(batch != null ? batch.getReceivedQty() : java.math.BigDecimal.ZERO);

        insp = inspectionRepo.save(insp);

        // Load inspection plan parameters if one exists
        final QualityInspection finalInsp = insp;
        planRepo.findByInspectionTypeAndMaterialIdAndActiveTrue("INCOMING", event.materialId())
                .ifPresent(plan -> {
                    int[] seq = {1};
                    for (InspectionPlanParameter param : plan.getParameters()) {
                        QualityInspectionItem item = new QualityInspectionItem();
                        item.setInspection(finalInsp);
                        item.setSeq(seq[0]++);
                        item.setParameterName(param.getParameterName());
                        item.setSpecification(param.getSpecification());
                        item.setLowerLimit(param.getLowerLimit());
                        item.setUpperLimit(param.getUpperLimit());
                        item.setUnit(param.getUnit());
                        item.setMandatory(param.isMandatory());
                        inspectionItemRepo.save(item);
                    }
                });
    }

    @Transactional
    public QualityInspectionDto submitResult(Long inspectionId, InspectionResultRequest req) {
        QualityInspection insp = requireInspection(inspectionId);
        if (!"PENDING".equals(insp.getStatus()) && !"IN_PROGRESS".equals(insp.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Inspection " + inspectionId + " is not open for results");
        }

        for (InspectionResultRequest.ItemResultRequest itemReq : req.items()) {
            QualityInspectionItem item = inspectionItemRepo.findById(itemReq.itemId())
                    .orElseThrow(() -> new NotFoundException("Inspection item not found: " + itemReq.itemId()));
            item.setActualValue(itemReq.actualValue());
            item.setActualText(itemReq.actualText());
            item.setDeviationNote(itemReq.deviationNote());
            item.computeResult(); // server-side pass/fail — ignores client-supplied result
            inspectionItemRepo.save(item);
        }

        insp.setAcceptedQty(req.acceptedQty());
        insp.setRejectedQty(req.rejectedQty());
        insp.setRemarks(req.remarks());
        insp.setInspectorId(currentUserId());
        insp.setInspectedAt(Instant.now());
        insp.setStatus("IN_PROGRESS");
        insp = inspectionRepo.save(insp);

        events.publishEvent(new InspectionResultsSubmittedEvent(insp.getId()));
        return toDto(insp);
    }

    @Transactional
    public QualityInspectionDto accept(Long inspectionId) {
        return accept(inspectionId, null, null);
    }

    /**
     * Accepts an incoming inspection. The accepted and rejected quantities must add up to the inspected quantity:
     * when the caller sends neither, the stored result quantities apply, and when nothing was stored the whole lot
     * is accepted. Accepted stock moves QUARANTINE to AVAILABLE and rejected stock QUARANTINE to REJECTED, both
     * through the posting service.
     */
    @Transactional
    public QualityInspectionDto accept(Long inspectionId, BigDecimal requestedAccepted, BigDecimal requestedRejected) {
        QualityInspection insp = requireInspection(inspectionId);
        rejectFirstArticle(insp);
        if (!"IN_PROGRESS".equals(insp.getStatus()) && !"PENDING".equals(insp.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Inspection must be in PENDING or IN_PROGRESS to accept");
        }

        // Check mandatory items are measured
        List<QualityInspectionItem> items = inspectionItemRepo.findByInspectionId(inspectionId);
        boolean anyMandatoryNotMeasured = items.stream()
                .filter(QualityInspectionItem::isMandatory)
                .anyMatch(i -> "NOT_MEASURED".equals(i.getResult()));
        if (anyMandatoryNotMeasured) {
            throw new BusinessRuleException(ErrorCode.INSPECTION_INCOMPLETE,
                    "All mandatory parameters must be measured before accepting");
        }

        BigDecimal inspected = insp.getInspectedQty() != null ? insp.getInspectedQty() : BigDecimal.ZERO;
        BigDecimal accepted = requestedAccepted;
        BigDecimal rejected = requestedRejected;
        if (accepted == null && rejected == null) {
            accepted = insp.getAcceptedQty() != null ? insp.getAcceptedQty() : BigDecimal.ZERO;
            rejected = insp.getRejectedQty() != null ? insp.getRejectedQty() : BigDecimal.ZERO;
            if (accepted.signum() == 0 && rejected.signum() == 0) {
                accepted = inspected;
            }
        } else if (rejected == null) {
            rejected = inspected.subtract(accepted);
        } else if (accepted == null) {
            accepted = inspected.subtract(rejected);
        }
        if (accepted.signum() < 0 || rejected.signum() < 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Accepted and rejected quantities cannot be negative");
        }
        if (accepted.add(rejected).compareTo(inspected) != 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Accepted (" + accepted.toPlainString() + ") plus rejected (" + rejected.toPlainString()
                            + ") must equal the inspected quantity (" + inspected.toPlainString() + ")");
        }
        if (accepted.signum() == 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Nothing is accepted; use Reject to reject the whole lot");
        }

        insp.setAcceptedQty(accepted);
        insp.setRejectedQty(rejected);
        insp.setStatus("APPROVED");
        insp.setDecisionBy(currentUserId());
        insp.setDecisionAt(Instant.now());
        insp = inspectionRepo.save(insp);

        // Change batch QUARANTINE → AVAILABLE
        if (insp.getMaterialBatchId() != null) {
            batchRepo.findById(insp.getMaterialBatchId()).ifPresent(batch -> {
                batch.setStatus("AVAILABLE");
                batchRepo.save(batch);
            });
            releaseQuarantine(insp, accepted, rejected);
        }

        // Update po_item accepted and rejected qty
        updatePoItemAccepted(insp);
        if (rejected.signum() > 0) {
            updatePoItemRejected(insp);
        }

        auditService.record("INSPECTION_ACCEPTED", "QUALITY_INSPECTION", String.valueOf(inspectionId),
                "IN_PROGRESS", "APPROVED",
                "accepted=" + accepted.toPlainString() + " rejected=" + rejected.toPlainString());
        return toDto(insp);
    }

    /**
     * Moves the batch's quarantined stock out of quarantine: the accepted quantity to AVAILABLE and the rejected
     * quantity to REJECTED, at the location where the goods were received.
     */
    private void releaseQuarantine(QualityInspection insp, BigDecimal accepted, BigDecimal rejected) {
        BigDecimal toAccept = accepted;
        BigDecimal toReject = rejected;
        List<LocationStock> quarantined = stockQuery
                .stockOfBatch("MATERIAL", insp.getMaterialId(), insp.getMaterialBatchId()).stream()
                .filter(b -> "QUARANTINE".equals(b.stockStatus()) || "HOLD".equals(b.stockStatus()))
                .toList();
        BigDecimal available = quarantined.stream().map(LocationStock::qtyOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal needed = accepted.add(rejected);
        if (available.compareTo(needed) < 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Batch " + insp.getMaterialBatchId() + " has only " + available.toPlainString()
                            + " in QUARANTINE; the decision needs " + needed.toPlainString());
        }
        for (LocationStock bal : quarantined) {
            BigDecimal here = bal.qtyOnHand();
            BigDecimal acceptHere = toAccept.min(here);
            toAccept = toAccept.subtract(acceptHere);
            BigDecimal rejectHere = toReject.min(here.subtract(acceptHere));
            toReject = toReject.subtract(rejectHere);
            moveQuarantined(insp, bal, acceptHere, "AVAILABLE", "Incoming inspection accepted");
            moveQuarantined(insp, bal, rejectHere, "REJECTED", "Incoming inspection rejected");
        }
    }

    private void moveQuarantined(QualityInspection insp, LocationStock bal, BigDecimal qty, String toStatus,
            String remarks) {
        if (qty.signum() <= 0) {
            return;
        }
        postingService.transfer(new TransferCommand(
                "MATERIAL", insp.getMaterialId(), null, insp.getMaterialBatchId(), null,
                bal.warehouseId(), bal.locationId(), bal.stockStatus(),
                bal.warehouseId(), bal.locationId(), toStatus,
                qty, null,
                "QUALITY_INSPECTION", insp.getId(), insp.getInspectionNumber(), remarks));
    }

    /**
     * Puts an incoming inspection on HOLD (QCI-04, QMS-003): the batch's quarantined stock moves QUARANTINE to HOLD
     * through the posting service, so it stays unavailable to issue, and the inspection waits for a re-inspection.
     * A reason is mandatory.
     */
    @Transactional
    public QualityInspectionDto hold(Long inspectionId, String reason) {
        QualityInspection insp = requireInspection(inspectionId);
        rejectFirstArticle(insp);
        if (insp.getMaterialBatchId() == null || !"INCOMING".equals(insp.getInspectionType())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Only an incoming inspection can be put on hold here; final inspections use the FG batch hold");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A reason is required to put a batch on hold");
        }
        if (!"IN_PROGRESS".equals(insp.getStatus()) && !"PENDING".equals(insp.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Inspection must be in PENDING or IN_PROGRESS to put on hold");
        }
        String from = insp.getStatus();
        for (LocationStock bal : stockQuery
                .stockOfBatch("MATERIAL", insp.getMaterialId(), insp.getMaterialBatchId())) {
            if ("QUARANTINE".equals(bal.stockStatus())) {
                moveQuarantined(insp, bal, bal.qtyOnHand(), "HOLD", "Incoming inspection on hold: " + reason.trim());
            }
        }
        batchRepo.findById(insp.getMaterialBatchId()).ifPresent(batch -> {
            batch.setStatus("HOLD");
            batchRepo.save(batch);
        });
        insp.setStatus("HOLD");
        insp.setRemarks(reason.trim());
        insp.setDecisionBy(currentUserId());
        insp.setDecisionAt(Instant.now());
        insp = inspectionRepo.save(insp);
        auditService.record("INSPECTION_HELD", "QUALITY_INSPECTION", String.valueOf(inspectionId), from, "HOLD",
                reason.trim());
        return toDto(insp);
    }

    /** Re-opens a held inspection for a new round of results (QCI-05). The stock stays on HOLD until a decision. */
    @Transactional
    public QualityInspectionDto reinspect(Long inspectionId) {
        QualityInspection insp = requireInspection(inspectionId);
        if (!"HOLD".equals(insp.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only an inspection on HOLD can be re-inspected");
        }
        insp.setStatus("IN_PROGRESS");
        insp = inspectionRepo.save(insp);
        auditService.record("INSPECTION_REINSPECTION", "QUALITY_INSPECTION", String.valueOf(inspectionId), "HOLD",
                "IN_PROGRESS", "Re-inspection started");
        return toDto(insp);
    }

    @Transactional
    public QualityInspectionDto reject(Long inspectionId, String reason) {
        QualityInspection insp = requireInspection(inspectionId);
        rejectFirstArticle(insp);
        if (!"IN_PROGRESS".equals(insp.getStatus()) && !"PENDING".equals(insp.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Inspection must be in PENDING or IN_PROGRESS to reject");
        }

        BigDecimal inspected = insp.getInspectedQty() != null ? insp.getInspectedQty() : BigDecimal.ZERO;
        insp.setAcceptedQty(BigDecimal.ZERO);
        insp.setRejectedQty(inspected);
        insp.setStatus("REJECTED");
        insp.setDecisionBy(currentUserId());
        insp.setDecisionAt(Instant.now());
        insp.setRemarks(reason);
        insp = inspectionRepo.save(insp);

        // Change batch QUARANTINE → REJECTED
        if (insp.getMaterialBatchId() != null) {
            batchRepo.findById(insp.getMaterialBatchId()).ifPresent(batch -> {
                batch.setStatus("REJECTED");
                batchRepo.save(batch);
            });
            releaseQuarantine(insp, BigDecimal.ZERO, inspected);
        }

        // Update po_item rejected qty
        updatePoItemRejected(insp);

        auditService.record("INSPECTION_REJECTED", "QUALITY_INSPECTION", String.valueOf(inspectionId),
                "IN_PROGRESS", "REJECTED", reason);
        return toDto(insp);
    }

    /**
     * Closes a first article inspection (R16-4.1): APPROVED when every item passed, REJECTED when any item failed.
     * Mandatory items must have been measured. The FAI workflow is the only caller; the generic accept and reject
     * endpoints refuse FAI inspections because they move incoming-batch stock status that an FAI does not have.
     */
    @Transactional
    public QualityInspectionDto closeFirstArticle(Long inspectionId, String remarks) {
        QualityInspection insp = requireInspection(inspectionId);
        if (!"FAI".equals(insp.getInspectionType())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Inspection " + inspectionId + " is not a first article inspection");
        }
        if (!"IN_PROGRESS".equals(insp.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Results must be submitted before the first article inspection is closed");
        }
        List<QualityInspectionItem> items = inspectionItemRepo.findByInspectionId(inspectionId);
        if (items.stream().filter(QualityInspectionItem::isMandatory)
                .anyMatch(i -> "NOT_MEASURED".equals(i.getResult()))) {
            throw new BusinessRuleException(ErrorCode.INSPECTION_INCOMPLETE,
                    "All mandatory parameters must be measured before the first article inspection is closed");
        }
        boolean passed = items.stream().noneMatch(i -> "FAIL".equals(i.getResult()));
        String target = passed ? "APPROVED" : "REJECTED";
        insp.setStatus(target);
        insp.setDecisionBy(currentUserId());
        insp.setDecisionAt(Instant.now());
        if (remarks != null && !remarks.isBlank()) {
            insp.setRemarks(remarks);
        }
        insp = inspectionRepo.save(insp);
        auditService.record("FAI_INSPECTION_CLOSED", "QUALITY_INSPECTION", String.valueOf(inspectionId),
                "IN_PROGRESS", target, remarks);
        return toDto(insp);
    }

    /**
     * Creates the FAI-type inspection for the first article of a production order, with one item per plan parameter
     * (R16-4.1). Called by the FAI workflow, which owns the requirement; results are then submitted like any other.
     */
    @Transactional
    public Long createFirstArticleInspection(Long productionOrderId, InspectionPlan plan) {
        QualityInspection insp = new QualityInspection();
        insp.setInspectionNumber(numberService.next("QI"));
        insp.setInspectionType("FAI");
        insp.setStatus("PENDING");
        insp.setProductionOrderId(productionOrderId);
        insp.setInspectedQty(java.math.BigDecimal.ONE);
        insp = inspectionRepo.save(insp);
        int seq = 1;
        for (InspectionPlanParameter param : plan.getParameters()) {
            QualityInspectionItem item = new QualityInspectionItem();
            item.setInspection(insp);
            item.setSeq(seq++);
            item.setParameterName(param.getParameterName());
            item.setSpecification(param.getSpecification());
            item.setLowerLimit(param.getLowerLimit());
            item.setUpperLimit(param.getUpperLimit());
            item.setUnit(param.getUnit());
            item.setMandatory(param.isMandatory());
            inspectionItemRepo.save(item);
        }
        auditService.record("FAI_INSPECTION_CREATED", "QUALITY_INSPECTION", String.valueOf(insp.getId()), null,
                insp.getInspectionNumber(), "Plan " + plan.getPlanCode());
        return insp.getId();
    }

    /**
     * Saves measured values on a first article inspection and records the outcome quantities: the one article is
     * accepted when no measured item failed, rejected otherwise. Item results are computed server-side from the
     * plan limits, exactly as for any other inspection.
     */
    @Transactional
    public QualityInspectionDto submitFirstArticleResults(Long inspectionId,
                                                           java.util.Map<Long, java.math.BigDecimal> valuesByItem) {
        QualityInspection insp = requireInspection(inspectionId);
        if (!"FAI".equals(insp.getInspectionType())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Inspection " + inspectionId + " is not a first article inspection");
        }
        List<QualityInspectionItem> items = inspectionItemRepo.findByInspectionId(inspectionId);
        boolean anyFail = false;
        List<InspectionResultRequest.ItemResultRequest> results = new java.util.ArrayList<>();
        for (QualityInspectionItem item : items) {
            java.math.BigDecimal value = valuesByItem.get(item.getId());
            if (value == null) {
                continue;
            }
            item.setActualValue(value);
            item.computeResult();
            anyFail |= "FAIL".equals(item.getResult());
            results.add(new InspectionResultRequest.ItemResultRequest(item.getId(), value, null, null));
        }
        if (results.isEmpty() || results.size() != valuesByItem.size()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Give a value for at least one item of this inspection and no item of another");
        }
        return submitResult(inspectionId, new InspectionResultRequest(
                anyFail ? java.math.BigDecimal.ZERO : java.math.BigDecimal.ONE,
                anyFail ? java.math.BigDecimal.ONE : java.math.BigDecimal.ZERO, null, results));
    }

    private static void rejectFirstArticle(QualityInspection insp) {
        if ("FAI".equals(insp.getInspectionType())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A first article inspection is completed through the FAI workflow");
        }
    }

    @Transactional(readOnly = true)
    public QualityInspectionDto findById(Long id) {
        return toDto(requireInspection(id));
    }

    @Transactional(readOnly = true)
    public Page<QualityInspectionDto> search(String type, String status, Pageable pageable) {
        return inspectionRepo.search(type, status, pageable).map(this::toDto);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private QualityInspection requireInspection(Long id) {
        return inspectionRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Quality inspection not found: " + id));
    }

    private void updatePoItemAccepted(QualityInspection insp) {
        if (insp.getAcceptedQty() == null || insp.getGoodsReceiptItemId() == null) return;
        // Find GR item → PO item
        // We don't have a direct GR item repo here; use batch to find PO item
        if (insp.getMaterialBatchId() != null) {
            batchRepo.findById(insp.getMaterialBatchId()).ifPresent(batch -> {
                if (batch.getPurchaseOrderId() != null) {
                    poItemRepo.findByPurchaseOrderId(batch.getPurchaseOrderId()).forEach(poi -> {
                        if (poi.getMaterialId().equals(insp.getMaterialId())) {
                            poi.setAcceptedQty(poi.getAcceptedQty().add(insp.getAcceptedQty()));
                            poItemRepo.save(poi);
                        }
                    });
                }
            });
        }
    }

    private void updatePoItemRejected(QualityInspection insp) {
        if (insp.getRejectedQty() == null || insp.getMaterialBatchId() == null) return;
        batchRepo.findById(insp.getMaterialBatchId()).ifPresent(batch -> {
            if (batch.getPurchaseOrderId() != null) {
                poItemRepo.findByPurchaseOrderId(batch.getPurchaseOrderId()).forEach(poi -> {
                    if (poi.getMaterialId().equals(insp.getMaterialId())) {
                        poi.setRejectedQty(poi.getRejectedQty().add(insp.getRejectedQty()));
                        poItemRepo.save(poi);
                    }
                });
            }
        });
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.springmfg.ims.auth.service.UserPermissionCache.CachedUser u) {
                return u.id();
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    private QualityInspectionDto toDto(QualityInspection insp) {
        List<QualityInspectionItem> items = inspectionItemRepo.findByInspectionId(insp.getId());
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
}
