package com.springmfg.ims.plm.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.engineering.bom.Bom;
import com.springmfg.ims.engineering.bom.BomDto;
import com.springmfg.ims.engineering.bom.BomRepository;
import com.springmfg.ims.engineering.bom.BomService;
import com.springmfg.ims.plm.domain.Eco;
import com.springmfg.ims.plm.domain.EcoAffectedItem;
import com.springmfg.ims.plm.domain.EcoItemType;
import com.springmfg.ims.plm.domain.EcoStatus;
import com.springmfg.ims.plm.domain.EcoUrgency;
import com.springmfg.ims.plm.dto.EcoDtos.EcoDto;
import com.springmfg.ims.plm.dto.EcoDtos.EcoItemDto;
import com.springmfg.ims.plm.dto.EcoDtos.EcoSummaryDto;
import com.springmfg.ims.plm.dto.EcoDtos.SaveEcoItemRequest;
import com.springmfg.ims.plm.dto.EcoDtos.SaveEcoRequest;
import com.springmfg.ims.plm.repository.EcoRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;

/**
 * Engineering change orders (PLM-001..003). Approval changes nothing in the BOM; {@link #implement} is the only step
 * that creates revisions, and it creates DRAFT copies of the active BOMs. The old ACTIVE BOM is superseded later,
 * when the new revision is activated through the normal BOM workflow.
 */
@Service
public class EcoService {

    private static final List<String> OPEN_REVISION_STATUSES = List.of("DRAFT", "ENGINEERING_REVIEW", "APPROVED");

    private final EcoRepository ecoRepo;
    private final ProductRepository productRepo;
    private final BomRepository bomRepo;
    private final BomService bomService;
    private final ProductLifecycleService lifecycleService;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    public EcoService(EcoRepository ecoRepo, ProductRepository productRepo, BomRepository bomRepo,
                      BomService bomService, ProductLifecycleService lifecycleService,
                      DocumentNumberService numberService, AuditService auditService) {
        this.ecoRepo = ecoRepo;
        this.productRepo = productRepo;
        this.bomRepo = bomRepo;
        this.bomService = bomService;
        this.lifecycleService = lifecycleService;
        this.numberService = numberService;
        this.auditService = auditService;
    }

    // ---- queries ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<EcoSummaryDto> search(String q, EcoStatus status, Pageable pageable) {
        return ecoRepo.search(q, status == null ? null : status.name(), pageable)
                .map(e -> new EcoSummaryDto(e.getId(), e.getEcoNumber(), e.getTitle(), e.getChangeCategory(),
                        e.getUrgency(), e.getStatus(), e.getEffectivityDate(), e.getInitiatedBy(),
                        e.getItems().size(), e.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public EcoDto get(Long id) {
        return toDto(load(id));
    }

    // ---- maintenance (DRAFT only) ---------------------------------------------------------------------------

    @Transactional
    public EcoDto create(SaveEcoRequest req) {
        Long userId = PlmSecurity.currentUserId();
        if (userId == null) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "An authenticated user is required to raise an ECO");
        }
        Eco eco = new Eco();
        eco.setEcoNumber(numberService.next("ECO"));
        eco.setInitiatedBy(userId);
        apply(eco, req);
        eco = ecoRepo.save(eco);
        auditService.record("ECO_CREATE", "Eco", String.valueOf(eco.getId()), null, eco.getEcoNumber(), null);
        return toDto(eco);
    }

    @Transactional
    public EcoDto update(Long id, SaveEcoRequest req) {
        Eco eco = load(id);
        if (eco.getStatus() != EcoStatus.DRAFT) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "An ECO can only be edited in DRAFT, current: " + eco.getStatus());
        }
        apply(eco, req);
        eco = ecoRepo.save(eco);
        auditService.record("ECO_UPDATE", "Eco", String.valueOf(id), null, eco.getEcoNumber(), null);
        return toDto(eco);
    }

    // ---- workflow ---------------------------------------------------------------------------------------------

    @Transactional
    public EcoDto submit(Long id) {
        Eco eco = load(id);
        if (eco.getItems().isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "An ECO must list at least one affected item before it is submitted for review");
        }
        return move(eco, EcoStatus.REVIEW, "ECO_SUBMIT", null);
    }

    @Transactional
    public EcoDto returnToDraft(Long id, String reason) {
        return move(load(id), EcoStatus.DRAFT, "ECO_RETURN", requireReason(reason));
    }

    @Transactional
    public EcoDto approve(Long id) {
        Eco eco = load(id);
        EcoStatus old = eco.getStatus();
        eco.setStatus(old.transitionTo(EcoStatus.APPROVED));
        eco.setApprovedBy(PlmSecurity.currentUserId());
        eco.setApprovedAt(Instant.now());
        eco.setStatusReason(null);
        eco = ecoRepo.save(eco);
        auditService.record("ECO_APPROVE", "Eco", String.valueOf(id), old.name(), EcoStatus.APPROVED.name(), null);
        return toDto(eco);
    }

    @Transactional
    public EcoDto cancel(Long id, String reason) {
        return move(load(id), EcoStatus.CANCELLED, "ECO_CANCEL", requireReason(reason));
    }

    /**
     * Creates a DRAFT revision of the active BOM for every BOM item, links old and new revisions on the item, and
     * records a lifecycle event for each affected product. All or nothing: one failing product rolls back every
     * revision created so far and leaves the ECO APPROVED.
     */
    @Transactional
    public EcoDto implement(Long id) {
        Eco eco = ecoRepo.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("ECO not found: " + id));
        eco.setStatus(eco.getStatus().transitionTo(EcoStatus.IMPLEMENTING));

        Set<Long> productIds = new LinkedHashSet<>();
        List<String> created = new ArrayList<>();
        for (EcoAffectedItem item : eco.getItems()) {
            productIds.add(item.getProductId());
            if (item.getItemType() != EcoItemType.BOM) {
                continue;
            }
            Bom active = bomRepo.findByProductIdAndStatus(item.getProductId(), "ACTIVE")
                    .orElseThrow(() -> new BusinessRuleException(ErrorCode.BOM_NOT_ACTIVE,
                            "Product " + productLabel(item.getProductId()) + " has no active BOM to revise"));
            if (!bomRepo.findByProductIdAndStatusIn(item.getProductId(), OPEN_REVISION_STATUSES).isEmpty()) {
                throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                        "Product " + productLabel(item.getProductId())
                                + " already has an open BOM revision; activate or discard it first");
            }
            BomDto copy = bomService.copyRevision(active.getId(),
                    truncate("ECO " + eco.getEcoNumber() + ": " + item.getChangeDescription(), 500));
            item.setOldBomId(active.getId());
            item.setNewBomId(copy.id());
            created.add(copy.bomNumber() + " rev " + copy.revision());
        }
        for (Long productId : productIds) {
            lifecycleService.recordEcoImplementation(productId, eco.getId(),
                    "ECO " + eco.getEcoNumber() + " implemented");
        }

        eco.setStatus(eco.getStatus().transitionTo(EcoStatus.IMPLEMENTED));
        eco.setImplementedAt(Instant.now());
        eco = ecoRepo.save(eco);
        auditService.record("ECO_IMPLEMENT", "Eco", String.valueOf(id), EcoStatus.APPROVED.name(),
                EcoStatus.IMPLEMENTED.name(),
                created.isEmpty() ? "No BOM revisions required" : "Created DRAFT revisions: " + String.join(", ", created));
        return toDto(eco);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private EcoDto move(Eco eco, EcoStatus target, String action, String reason) {
        EcoStatus old = eco.getStatus();
        eco.setStatus(old.transitionTo(target));
        eco.setStatusReason(reason);
        eco = ecoRepo.save(eco);
        auditService.record(action, "Eco", String.valueOf(eco.getId()), old.name(), target.name(), reason);
        return toDto(eco);
    }

    private void apply(Eco eco, SaveEcoRequest req) {
        eco.setTitle(req.title().trim());
        eco.setDescription(req.description());
        eco.setReason(req.reason().trim());
        eco.setChangeCategory(req.changeCategory());
        eco.setUrgency(req.urgency() == null ? EcoUrgency.NORMAL : req.urgency());
        eco.setEffectivityDate(req.effectivityDate());
        eco.setImpactAssessment(req.impactAssessment());
        syncItems(eco, req.items() == null ? List.of() : req.items());
    }

    /** Updates items in place by (type, product) so re-saving an unchanged item never trips the unique key. */
    private void syncItems(Eco eco, List<SaveEcoItemRequest> requested) {
        Set<String> seen = new HashSet<>();
        for (SaveEcoItemRequest r : requested) {
            if (!seen.add(r.itemType() + ":" + r.productId())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Duplicate affected item: " + r.itemType() + " for product " + r.productId());
            }
            if (!productRepo.existsById(r.productId())) {
                throw new NotFoundException("Product not found: " + r.productId());
            }
        }
        eco.getItems().removeIf(i -> !seen.contains(i.getItemType() + ":" + i.getProductId()));
        for (SaveEcoItemRequest r : requested) {
            Optional<EcoAffectedItem> existing = eco.getItems().stream()
                    .filter(i -> i.getItemType() == r.itemType() && i.getProductId().equals(r.productId()))
                    .findFirst();
            EcoAffectedItem item = existing.orElseGet(() -> {
                EcoAffectedItem n = new EcoAffectedItem();
                n.setEco(eco);
                n.setItemType(r.itemType());
                n.setProductId(r.productId());
                eco.getItems().add(n);
                return n;
            });
            item.setChangeDescription(r.changeDescription().trim());
        }
    }

    private Eco load(Long id) {
        return ecoRepo.findById(id).orElseThrow(() -> new NotFoundException("ECO not found: " + id));
    }

    private String productLabel(Long productId) {
        return productRepo.findById(productId).map(Product::getProductCode).orElse(String.valueOf(productId));
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A reason is required");
        }
        return reason.trim();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private EcoDto toDto(Eco e) {
        List<EcoItemDto> items = e.getItems().stream().map(i -> {
            Product p = productRepo.findById(i.getProductId()).orElse(null);
            return new EcoItemDto(i.getId(), i.getItemType(), i.getProductId(),
                    p == null ? null : p.getProductCode(), p == null ? null : p.getName(),
                    i.getOldBomId(), bomLabel(i.getOldBomId()), i.getNewBomId(), bomLabel(i.getNewBomId()),
                    i.getChangeDescription());
        }).toList();
        return new EcoDto(e.getId(), e.getEcoNumber(), e.getTitle(), e.getDescription(), e.getReason(),
                e.getChangeCategory(), e.getUrgency(), e.getEffectivityDate(), e.getImpactAssessment(),
                e.getStatus(), e.getInitiatedBy(), e.getApprovedBy(), e.getApprovedAt(), e.getImplementedAt(),
                e.getStatusReason(), e.getCreatedAt(), items, allowedActions(e.getStatus()));
    }

    private String bomLabel(Long bomId) {
        if (bomId == null) {
            return null;
        }
        return bomRepo.findById(bomId).map(b -> b.getBomNumber() + " rev " + b.getRevision()).orElse(null);
    }

    private static List<String> allowedActions(EcoStatus status) {
        return switch (status) {
            case DRAFT -> List.of("edit", "submit", "cancel");
            case REVIEW -> List.of("approve", "return", "cancel");
            case APPROVED -> List.of("implement", "cancel");
            default -> List.of();
        };
    }
}
