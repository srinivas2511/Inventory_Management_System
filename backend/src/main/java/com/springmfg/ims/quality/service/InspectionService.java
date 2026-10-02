package com.springmfg.ims.quality.service;

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
import com.springmfg.ims.inventory.domain.MaterialBatch;
import com.springmfg.ims.inventory.repository.MaterialBatchRepository;
import com.springmfg.ims.procurement.event.GoodsReceivedForInspectionEvent;
import com.springmfg.ims.procurement.repository.PurchaseOrderItemRepository;
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

    public InspectionService(
            QualityInspectionRepository inspectionRepo,
            QualityInspectionItemRepository inspectionItemRepo,
            InspectionPlanRepository planRepo,
            MaterialBatchRepository batchRepo,
            PurchaseOrderItemRepository poItemRepo,
            DocumentNumberService numberService,
            AuditService auditService) {
        this.inspectionRepo = inspectionRepo;
        this.inspectionItemRepo = inspectionItemRepo;
        this.planRepo = planRepo;
        this.batchRepo = batchRepo;
        this.poItemRepo = poItemRepo;
        this.numberService = numberService;
        this.auditService = auditService;
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

        return toDto(insp);
    }

    @Transactional
    public QualityInspectionDto accept(Long inspectionId) {
        QualityInspection insp = requireInspection(inspectionId);
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
        }

        // Update po_item accepted qty
        updatePoItemAccepted(insp);

        auditService.record("INSPECTION_ACCEPTED", "QUALITY_INSPECTION", String.valueOf(inspectionId),
                "IN_PROGRESS", "APPROVED", null);
        return toDto(insp);
    }

    @Transactional
    public QualityInspectionDto reject(Long inspectionId, String reason) {
        QualityInspection insp = requireInspection(inspectionId);
        if (!"IN_PROGRESS".equals(insp.getStatus()) && !"PENDING".equals(insp.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Inspection must be in PENDING or IN_PROGRESS to reject");
        }

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
        }

        // Update po_item rejected qty
        updatePoItemRejected(insp);

        auditService.record("INSPECTION_REJECTED", "QUALITY_INSPECTION", String.valueOf(inspectionId),
                "IN_PROGRESS", "REJECTED", reason);
        return toDto(insp);
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
            if (auth != null && auth.getPrincipal() instanceof com.springmfg.ims.iam.domain.User u) {
                return u.getId();
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
                        i.getActualText(), i.getUnit(), i.getResult(), i.getDeviationNote(), i.isMandatory()))
                .toList();
        return new QualityInspectionDto(
                insp.getId(), insp.getInspectionNumber(), insp.getInspectionType(), insp.getStatus(),
                insp.getGoodsReceiptItemId(), null,
                insp.getMaterialBatchId(), null,
                insp.getMaterialId(), null,
                insp.getInspectedQty(), insp.getSampleSize(),
                insp.getAcceptedQty(), insp.getRejectedQty(),
                insp.getInspectorId(), insp.getInspectedAt(), insp.getRemarks(), itemDtos);
    }
}
