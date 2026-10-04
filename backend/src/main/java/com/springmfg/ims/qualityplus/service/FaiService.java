package com.springmfg.ims.qualityplus.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.event.EcoImplementedEvent;
import com.springmfg.ims.common.event.ProductLifecycleChangedEvent;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.inventory.domain.FgBatch;
import com.springmfg.ims.inventory.repository.FgBatchRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.production.domain.ProductionOrder;
import com.springmfg.ims.production.repository.ProductionOrderRepository;
import com.springmfg.ims.quality.domain.InspectionPlan;
import com.springmfg.ims.quality.domain.QualityInspection;
import com.springmfg.ims.quality.domain.QualityInspectionItem;
import com.springmfg.ims.quality.dto.QualityInspectionDto;
import com.springmfg.ims.quality.repository.InspectionPlanRepository;
import com.springmfg.ims.quality.repository.QualityInspectionItemRepository;
import com.springmfg.ims.quality.repository.QualityInspectionRepository;
import com.springmfg.ims.quality.service.InspectionService;
import com.springmfg.ims.qualityplus.domain.FaiStatus;
import com.springmfg.ims.qualityplus.domain.FaiTrigger;
import com.springmfg.ims.qualityplus.domain.FirstArticleInspection;
import com.springmfg.ims.qualityplus.dto.FaiDtos.CompleteFaiRequest;
import com.springmfg.ims.qualityplus.dto.FaiDtos.FaiDto;
import com.springmfg.ims.qualityplus.dto.FaiDtos.FaiReportDto;
import com.springmfg.ims.qualityplus.dto.FaiDtos.FaiReportItemDto;
import com.springmfg.ims.qualityplus.dto.FaiDtos.FaiResultValue;
import com.springmfg.ims.qualityplus.dto.FaiDtos.FaiSummaryDto;
import com.springmfg.ims.qualityplus.dto.FaiDtos.StartFaiRequest;
import com.springmfg.ims.qualityplus.dto.FaiDtos.SubmitFaiResultsRequest;
import com.springmfg.ims.qualityplus.repository.FirstArticleInspectionRepository;

/**
 * First article inspection (QMS-010). An FAI is required automatically when an ECO is implemented for a product and
 * when a product is approved for the first time, or raised by hand. Starting it links the product's inspection plan
 * to the first production order and creates an FAI-type inspection from the plan; once results are in, completing it
 * closes that inspection and records PASSED or FAILED. Only one FAI per product is open at a time.
 */
@Service
public class FaiService {

    private static final EnumSet<FaiStatus> OPEN = EnumSet.of(FaiStatus.REQUIRED, FaiStatus.IN_PROGRESS);

    private final FirstArticleInspectionRepository repo;
    private final ProductRepository productRepo;
    private final InspectionPlanRepository planRepo;
    private final QualityInspectionRepository inspectionRepo;
    private final QualityInspectionItemRepository itemRepo;
    private final ProductionOrderRepository orderRepo;
    private final FgBatchRepository fgBatchRepo;
    private final InspectionService inspectionService;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    public FaiService(FirstArticleInspectionRepository repo, ProductRepository productRepo,
                      InspectionPlanRepository planRepo, QualityInspectionRepository inspectionRepo,
                      QualityInspectionItemRepository itemRepo, ProductionOrderRepository orderRepo,
                      FgBatchRepository fgBatchRepo, InspectionService inspectionService,
                      DocumentNumberService numberService, AuditService auditService) {
        this.repo = repo;
        this.productRepo = productRepo;
        this.planRepo = planRepo;
        this.inspectionRepo = inspectionRepo;
        this.itemRepo = itemRepo;
        this.orderRepo = orderRepo;
        this.fgBatchRepo = fgBatchRepo;
        this.inspectionService = inspectionService;
        this.numberService = numberService;
        this.auditService = auditService;
    }

    // ---- automatic triggers (same transaction as the event's publisher) ---------------------------------------

    @EventListener
    @Transactional
    public void onEcoImplemented(EcoImplementedEvent event) {
        for (Long productId : event.productIds()) {
            require(productId, FaiTrigger.ECO_IMPLEMENTED, event.ecoId(), event.ecoNumber());
        }
    }

    /** A product approved for the first time needs a first article before series production. */
    @EventListener
    @Transactional
    public void onLifecycleChanged(ProductLifecycleChangedEvent event) {
        if ("APPROVED".equals(event.toStatus())
                && !repo.existsByProductIdAndStatus(event.productId(), FaiStatus.PASSED)) {
            require(event.productId(), FaiTrigger.NEW_PRODUCT, null, "Lifecycle APPROVED");
        }
    }

    // ---- queries ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<FaiSummaryDto> search(String q, FaiStatus status, Pageable pageable) {
        return repo.search(q, status == null ? null : status.name(), pageable).map(f -> {
            Product p = productRepo.findById(f.getProductId()).orElse(null);
            return new FaiSummaryDto(f.getId(), f.getFaiNumber(), p == null ? null : p.getProductCode(),
                    p == null ? null : p.getName(), f.getTriggerType(), f.getTriggerReference(), f.getStatus(),
                    f.getCreatedAt(), f.getCompletedAt());
        });
    }

    @Transactional(readOnly = true)
    public FaiDto get(Long id) {
        return toDto(load(id));
    }

    /** Data for the printable first article report: the inspected parameters with specification and result. */
    @Transactional(readOnly = true)
    public FaiReportDto report(Long id) {
        FirstArticleInspection f = load(id);
        if (f.getInspectionId() == null) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "The first article inspection has not been started, so there is no report yet");
        }
        List<FaiReportItemDto> items = itemRepo.findByInspectionId(f.getInspectionId()).stream()
                .sorted(Comparator.comparing(QualityInspectionItem::getSeq))
                .map(i -> new FaiReportItemDto(i.getId(), i.getSeq(), i.getParameterName(), i.getSpecification(),
                        i.getLowerLimit(), i.getUpperLimit(), i.getUnit(), i.getActualValue(), i.getActualText(),
                        i.getResult(), i.isMandatory()))
                .toList();
        return new FaiReportDto(toDto(f), items, Instant.now());
    }

    // ---- workflow ------------------------------------------------------------------------------------------------

    @Transactional
    public FaiDto createManual(Long productId) {
        return require(productId, FaiTrigger.MANUAL, null, "Raised manually")
                .map(this::toDto)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                        "An open first article inspection already exists for this product"));
    }

    @Transactional
    public FaiDto start(Long id, StartFaiRequest req) {
        FirstArticleInspection f = repo.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("First article inspection not found: " + id));
        FaiStatus old = f.getStatus();
        f.setStatus(old.transitionTo(FaiStatus.IN_PROGRESS));
        ProductionOrder order = orderRepo.findById(req.productionOrderId())
                .orElseThrow(() -> new NotFoundException("Production order not found: " + req.productionOrderId()));
        if (!f.getProductId().equals(order.getProductId())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Production order " + order.getOrderNumber() + " is not for the product being qualified");
        }
        final Long productId = f.getProductId();
        InspectionPlan plan = planRepo.findByInspectionTypeAndProductIdAndActiveTrue("FINAL", productId)
                .or(() -> planRepo.findByInspectionTypeAndProductIdAndActiveTrue("IN_PROCESS", productId))
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "The product has no active inspection plan to base the first article inspection on"));

        Long inspectionId = inspectionService.createFirstArticleInspection(order.getId(), plan);
        f.setInspectionPlanId(plan.getId());
        f.setProductionOrderId(order.getId());
        f.setInspectionId(inspectionId);
        f = repo.save(f);
        auditService.record("FAI_START", "FirstArticleInspection", String.valueOf(id), old.name(),
                FaiStatus.IN_PROGRESS.name(), "Order " + order.getOrderNumber() + ", plan " + plan.getPlanCode());
        return toDto(f);
    }

    /** Saves the measured values of a started FAI; they can be resubmitted until the FAI is completed. */
    @Transactional
    public FaiReportDto submitResults(Long id, SubmitFaiResultsRequest req) {
        FirstArticleInspection f = repo.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("First article inspection not found: " + id));
        if (f.getStatus() != FaiStatus.IN_PROGRESS || f.getInspectionId() == null) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Results can only be entered while the first article inspection is in progress, current: "
                            + f.getStatus());
        }
        java.util.Map<Long, java.math.BigDecimal> values = new java.util.HashMap<>();
        for (FaiResultValue v : req.values()) {
            if (values.put(v.itemId(), v.actualValue()) != null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "An item was given twice");
            }
        }
        inspectionService.submitFirstArticleResults(f.getInspectionId(), values);
        auditService.record("FAI_RESULTS", "FirstArticleInspection", String.valueOf(id), null,
                values.size() + " value(s) recorded", null);
        return report(id);
    }

    @Transactional
    public FaiDto complete(Long id, CompleteFaiRequest req) {
        FirstArticleInspection f = repo.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("First article inspection not found: " + id));
        FaiStatus old = f.getStatus();
        if (old != FaiStatus.IN_PROGRESS || f.getInspectionId() == null) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only a started first article inspection can be completed, current: " + old);
        }
        String summary = req == null ? null : req.summary();
        QualityInspectionDto closed = inspectionService.closeFirstArticle(f.getInspectionId(), summary);
        boolean passed = "APPROVED".equals(closed.status());
        FaiStatus target = old.transitionTo(passed ? FaiStatus.PASSED : FaiStatus.FAILED);
        f.setStatus(target);
        final FirstArticleInspection fai = f;
        f.setResultSummary(summary != null && !summary.isBlank() ? summary.trim() : summaryOf(f.getInspectionId()));
        f.setCompletedBy(QualitySecurity.currentUserId());
        f.setCompletedAt(Instant.now());
        if (f.getProductionOrderId() != null) {
            fgBatchRepo.findByProductionOrderId(f.getProductionOrderId()).map(FgBatch::getId)
                    .ifPresent(fai::setFgBatchId);
        }
        f = repo.save(f);
        auditService.record("FAI_COMPLETE", "FirstArticleInspection", String.valueOf(id), old.name(), target.name(),
                f.getResultSummary());
        return toDto(f);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    /** Creates a REQUIRED FAI unless the product already has one open; empty when one was already open. */
    private Optional<FirstArticleInspection> require(Long productId, FaiTrigger trigger, Long ecoId,
                                                     String reference) {
        if (!productRepo.existsById(productId)) {
            throw new NotFoundException("Product not found: " + productId);
        }
        if (repo.existsByProductIdAndStatusIn(productId, OPEN)) {
            return Optional.empty();
        }
        FirstArticleInspection f = new FirstArticleInspection();
        f.setFaiNumber(numberService.next("FAI"));
        f.setProductId(productId);
        f.setTriggerType(trigger);
        f.setEcoId(ecoId);
        f.setTriggerReference(reference);
        f = repo.save(f);
        auditService.record("FAI_REQUIRED", "FirstArticleInspection", String.valueOf(f.getId()), null,
                f.getFaiNumber(), trigger + (reference == null ? "" : " " + reference));
        return Optional.of(f);
    }

    private String summaryOf(Long inspectionId) {
        List<QualityInspectionItem> items = itemRepo.findByInspectionId(inspectionId);
        long passed = items.stream().filter(i -> "PASS".equals(i.getResult())).count();
        return passed + " of " + items.size() + " parameters passed";
    }

    private FirstArticleInspection load(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("First article inspection not found: " + id));
    }

    private FaiDto toDto(FirstArticleInspection f) {
        Product p = productRepo.findById(f.getProductId()).orElse(null);
        String planCode = f.getInspectionPlanId() == null ? null
                : planRepo.findById(f.getInspectionPlanId()).map(InspectionPlan::getPlanCode).orElse(null);
        String orderNumber = f.getProductionOrderId() == null ? null
                : orderRepo.findById(f.getProductionOrderId()).map(ProductionOrder::getOrderNumber).orElse(null);
        String batchNumber = f.getFgBatchId() == null ? null
                : fgBatchRepo.findById(f.getFgBatchId()).map(FgBatch::getBatchNumber).orElse(null);
        String inspectionNumber = f.getInspectionId() == null ? null
                : inspectionRepo.findById(f.getInspectionId()).map(QualityInspection::getInspectionNumber)
                        .orElse(null);
        return new FaiDto(f.getId(), f.getFaiNumber(), f.getProductId(), p == null ? null : p.getProductCode(),
                p == null ? null : p.getName(), f.getTriggerType(), f.getTriggerReference(), f.getInspectionPlanId(),
                planCode, f.getProductionOrderId(), orderNumber, f.getFgBatchId(), batchNumber, f.getInspectionId(),
                inspectionNumber, f.getStatus(), f.getStatus().next(), f.getResultSummary(), f.getCompletedBy(),
                f.getCompletedAt(), f.getCreatedAt());
    }
}
