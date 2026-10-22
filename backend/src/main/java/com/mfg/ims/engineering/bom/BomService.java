package com.mfg.ims.engineering.bom;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.masterdata.operation.OperationRepository;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.product.domain.Product;
import com.mfg.ims.product.repository.ProductRepository;

@Service
public class BomService {

    private final BomRepository bomRepo;
    private final BomItemRepository itemRepo;
    private final ProductRepository productRepo;
    private final MaterialRepository materialRepo;
    private final OperationRepository operationRepo;
    private final DocumentNumberService numberService;
    private final SystemSettingService settingService;
    private final AuditService auditService;

    public BomService(BomRepository bomRepo,
                      BomItemRepository itemRepo,
                      ProductRepository productRepo,
                      MaterialRepository materialRepo,
                      OperationRepository operationRepo,
                      DocumentNumberService numberService,
                      SystemSettingService settingService,
                      AuditService auditService) {
        this.bomRepo = bomRepo;
        this.itemRepo = itemRepo;
        this.productRepo = productRepo;
        this.materialRepo = materialRepo;
        this.operationRepo = operationRepo;
        this.numberService = numberService;
        this.settingService = settingService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public BomDto findById(Long id) {
        Bom bom = bomRepo.findById(id).orElseThrow(() -> new NotFoundException("BOM not found: " + id));
        return toDto(bom);
    }

    @Transactional(readOnly = true)
    public Page<BomDto> search(Long productId, String status, Pageable pageable) {
        return bomRepo.search(productId, status, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public BomDto findActiveBomForProduct(Long productId) {
        Bom bom = bomRepo.findByProductIdAndStatus(productId, "ACTIVE")
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.BOM_NOT_ACTIVE,
                        "No active BOM for product " + productId));
        return toDto(bom);
    }

    @Transactional
    public BomDto create(CreateBomRequest req) {
        Product product = productRepo.findById(req.productId())
                .orElseThrow(() -> new NotFoundException("Product not found: " + req.productId()));

        java.math.BigDecimal defaultBaseQty = settingService.getDecimal("bom.default_base_quantity", java.math.BigDecimal.ONE);
        java.math.BigDecimal baseQty = req.baseQuantity() != null ? req.baseQuantity() : defaultBaseQty;

        Bom bom = new Bom();
        bom.setBomNumber(numberService.next("BOM"));
        bom.setProductId(req.productId());
        bom.setBaseQuantity(baseQty);
        bom.setChangeReason(req.changeReason());
        bom.setStatus("DRAFT");

        bom = bomRepo.save(bom);
        setItems(bom, req.items());
        bom = bomRepo.save(bom);

        auditService.record("BOM_CREATE", "Bom", String.valueOf(bom.getId()), null, bom.getBomNumber(), null);
        return toDto(bom);
    }

    @Transactional
    public BomDto update(Long id, CreateBomRequest req) {
        Bom bom = bomRepo.findById(id).orElseThrow(() -> new NotFoundException("BOM not found: " + id));
        if (!"DRAFT".equals(bom.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "BOM can only be updated in DRAFT status, current: " + bom.getStatus());
        }
        if (req.baseQuantity() != null) bom.setBaseQuantity(req.baseQuantity());
        if (req.changeReason() != null) bom.setChangeReason(req.changeReason());

        bom.getItems().clear();
        setItems(bom, req.items());
        bom = bomRepo.save(bom);

        auditService.record("BOM_UPDATE", "Bom", String.valueOf(id), null, null, null);
        return toDto(bom);
    }

    @Transactional
    public BomDto submitForReview(Long id) {
        Bom bom = bomRepo.findById(id).orElseThrow(() -> new NotFoundException("BOM not found: " + id));
        assertTransition(bom, "DRAFT", "ENGINEERING_REVIEW");
        bom.setStatus("ENGINEERING_REVIEW");
        bom = bomRepo.save(bom);
        auditService.record("BOM_SUBMIT", "Bom", String.valueOf(id), "DRAFT", "ENGINEERING_REVIEW", null);
        return toDto(bom);
    }

    @Transactional
    public BomDto approve(Long id) {
        Bom bom = bomRepo.findById(id).orElseThrow(() -> new NotFoundException("BOM not found: " + id));
        assertTransition(bom, "ENGINEERING_REVIEW", "APPROVED");
        bom.setStatus("APPROVED");
        bom.setApprovedBy(currentUserId());
        bom.setApprovedAt(Instant.now());
        bom = bomRepo.save(bom);
        auditService.record("BOM_APPROVE", "Bom", String.valueOf(id), "ENGINEERING_REVIEW", "APPROVED", null);
        return toDto(bom);
    }

    @Transactional
    public BomDto activate(Long id) {
        Bom bom = bomRepo.findById(id).orElseThrow(() -> new NotFoundException("BOM not found: " + id));
        assertTransition(bom, "APPROVED", "ACTIVE");

        // Supersede any existing ACTIVE BOM for same product. Flushed before the new BOM becomes ACTIVE so the
        // one-active-per-product index (ux_bom_one_active) never sees two ACTIVE rows, whatever the flush order.
        bomRepo.findByProductIdAndStatus(bom.getProductId(), "ACTIVE").ifPresent(prev -> {
            prev.setStatus("SUPERSEDED");
            prev.setEffectiveTo(LocalDate.now());
            bomRepo.saveAndFlush(prev);
        });

        bom.setStatus("ACTIVE");
        bom.setEffectiveFrom(LocalDate.now());
        bom = bomRepo.save(bom);
        auditService.record("BOM_ACTIVATE", "Bom", String.valueOf(id), "APPROVED", "ACTIVE", null);
        return toDto(bom);
    }

    @Transactional
    public BomDto recall(Long id) {
        Bom bom = bomRepo.findById(id).orElseThrow(() -> new NotFoundException("BOM not found: " + id));
        assertTransition(bom, "ENGINEERING_REVIEW", "DRAFT");
        bom.setStatus("DRAFT");
        bom = bomRepo.save(bom);
        auditService.record("BOM_RECALL", "Bom", String.valueOf(id), "ENGINEERING_REVIEW", "DRAFT", null);
        return toDto(bom);
    }

    @Transactional
    public BomDto copyRevision(Long id) {
        return copyRevision(id, null);
    }

    /** Copies an APPROVED or ACTIVE BOM into the next DRAFT revision; a null reason keeps the default wording. */
    @Transactional
    public BomDto copyRevision(Long id, String changeReason) {
        Bom source = bomRepo.findById(id).orElseThrow(() -> new NotFoundException("BOM not found: " + id));
        if (!"APPROVED".equals(source.getStatus()) && !"ACTIVE".equals(source.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Can only copy revision from APPROVED or ACTIVE BOM, current: " + source.getStatus());
        }

        String newRevision = nextRevision(source.getRevision());

        Bom copy = new Bom();
        copy.setBomNumber(source.getBomNumber());
        copy.setRevision(newRevision);
        copy.setProductId(source.getProductId());
        copy.setStatus("DRAFT");
        copy.setBaseQuantity(source.getBaseQuantity());
        copy.setChangeReason(changeReason != null ? changeReason : "Copied from revision " + source.getRevision());
        copy = bomRepo.save(copy);

        List<BomItem> copiedItems = new ArrayList<>();
        for (BomItem src : source.getItems()) {
            BomItem item = new BomItem();
            item.setBom(copy);
            item.setLineNo(src.getLineNo());
            item.setItemKind(src.getItemKind());
            item.setMaterialId(src.getMaterialId());
            item.setOperationId(src.getOperationId());
            item.setRequiredQty(src.getRequiredQty());
            item.setUom(src.getUom());
            item.setScrapPct(src.getScrapPct());
            item.setInstruction(src.getInstruction());
            copiedItems.add(item);
        }
        copy.getItems().addAll(copiedItems);
        copy = bomRepo.save(copy);

        auditService.record("BOM_COPY_REVISION", "Bom", String.valueOf(copy.getId()),
                source.getBomNumber() + "/" + source.getRevision(),
                copy.getBomNumber() + "/" + copy.getRevision(), null);
        return toDto(copy);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void assertTransition(Bom bom, String expected, String target) {
        if (!expected.equals(bom.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "BOM " + bom.getBomNumber() + " must be in " + expected + " to move to " + target
                            + ", current: " + bom.getStatus());
        }
    }

    private void setItems(Bom bom, List<BomItemRequest> requests) {
        if (requests == null || requests.isEmpty()) return;
        for (BomItemRequest req : requests) {
            BomItem item = new BomItem();
            item.setBom(bom);
            item.setLineNo(req.lineNo());
            item.setItemKind(req.itemKind());
            item.setMaterialId(req.materialId());
            item.setOperationId(req.operationId());
            item.setRequiredQty(req.requiredQty());
            item.setUom(req.uom());
            item.setScrapPct(req.scrapPct() != null ? req.scrapPct() : java.math.BigDecimal.ZERO);
            item.setInstruction(req.instruction());
            bom.getItems().add(item);
        }
    }

    private BomDto toDto(Bom bom) {
        Product product = productRepo.findById(bom.getProductId()).orElse(null);
        List<BomItemDto> itemDtos = bom.getItems().stream().map(item -> {
            String matCode = null, matName = null;
            if (item.getMaterialId() != null) {
                Material mat = materialRepo.findById(item.getMaterialId()).orElse(null);
                if (mat != null) { matCode = mat.getMaterialCode(); matName = mat.getName(); }
            }
            String opName = null;
            if (item.getOperationId() != null) {
                opName = operationRepo.findById(item.getOperationId()).map(o -> o.getName()).orElse(null);
            }
            return new BomItemDto(item.getId(), item.getLineNo(), item.getItemKind(),
                    item.getMaterialId(), matCode, matName,
                    item.getOperationId(), opName,
                    item.getRequiredQty(), item.getUom(), item.getScrapPct(), item.getInstruction());
        }).toList();

        return new BomDto(
                bom.getId(),
                bom.getBomNumber(),
                bom.getRevision(),
                bom.getProductId(),
                product != null ? product.getProductCode() : null,
                product != null ? product.getName() : null,
                bom.getStatus(),
                bom.getEffectiveFrom(),
                bom.getEffectiveTo(),
                bom.getBaseQuantity(),
                bom.getChangeReason(),
                bom.getApprovedBy(),
                bom.getApprovedAt(),
                itemDtos,
                allowedActions(bom.getStatus()));
    }

    private List<String> allowedActions(String status) {
        return switch (status) {
            case "DRAFT" -> List.of("submit", "copy_revision");
            case "ENGINEERING_REVIEW" -> List.of("approve", "recall");
            case "APPROVED" -> List.of("activate", "copy_revision");
            case "ACTIVE" -> List.of("copy_revision", "obsolete");
            default -> List.of();
        };
    }

    private String nextRevision(String current) {
        if (current == null || current.isEmpty()) return "B";
        char last = current.charAt(current.length() - 1);
        if (last == 'Z') return current + "A";
        return current.substring(0, current.length() - 1) + (char)(last + 1);
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return null;
    }
}
