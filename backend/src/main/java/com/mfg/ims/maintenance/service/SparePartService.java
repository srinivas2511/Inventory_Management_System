package com.mfg.ims.maintenance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.dto.PostingResult;
import com.mfg.ims.inventory.service.BatchAllocator;
import com.mfg.ims.inventory.service.BatchAllocator.Allocation;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.inventory.service.InventoryQueryService;
import com.mfg.ims.maintenance.domain.MaintenanceRequest;
import com.mfg.ims.maintenance.domain.MaintenanceStatus;
import com.mfg.ims.maintenance.domain.SparePart;
import com.mfg.ims.maintenance.domain.SparePartIssueLine;
import com.mfg.ims.maintenance.dto.SparePartDtos.IssueSparePartRequest;
import com.mfg.ims.maintenance.dto.SparePartDtos.SaveSparePartRequest;
import com.mfg.ims.maintenance.dto.SparePartDtos.SparePartDto;
import com.mfg.ims.maintenance.dto.SparePartDtos.SparePartIssueLineDto;
import com.mfg.ims.maintenance.repository.MaintenanceRequestRepository;
import com.mfg.ims.maintenance.repository.SparePartIssueLineRepository;
import com.mfg.ims.maintenance.repository.SparePartRepository;
import com.mfg.ims.material.dto.MaterialDto;
import com.mfg.ims.material.dto.SaveMaterialRequest;
import com.mfg.ims.material.service.MaterialService;

/**
 * Spare part master and issues. A spare part owns a material of type SPARE_PART, so receiving, counting and
 * issuing it all use the ordinary inventory ledger; this service only ever asks {@link InventoryPostingService}
 * to move stock. Minimum stock lives on the material too, which is what the low-stock sweep and MRP read.
 */
@Service
public class SparePartService {

    static final String MATERIAL_TYPE = "SPARE_PART";
    static final String ISSUE_REFERENCE = "SPARE_PART_ISSUE";

    private final SparePartRepository partRepo;
    private final SparePartIssueLineRepository issueRepo;
    private final MaintenanceRequestRepository requestRepo;
    private final MaterialService materialService;
    private final InventoryQueryService inventoryQuery;
    private final BatchAllocator allocator;
    private final InventoryPostingService postingService;
    private final AuditService auditService;

    public SparePartService(SparePartRepository partRepo, SparePartIssueLineRepository issueRepo,
                            MaintenanceRequestRepository requestRepo, MaterialService materialService,
                            InventoryQueryService inventoryQuery, BatchAllocator allocator,
                            InventoryPostingService postingService, AuditService auditService) {
        this.partRepo = partRepo;
        this.issueRepo = issueRepo;
        this.requestRepo = requestRepo;
        this.materialService = materialService;
        this.inventoryQuery = inventoryQuery;
        this.allocator = allocator;
        this.postingService = postingService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<SparePartDto> list(String q, Boolean active, Pageable pageable) {
        Page<SparePart> page = partRepo.search(q, active, pageable);
        Map<Long, BigDecimal> onHand = inventoryQuery.availableQtyByMaterial(
                page.getContent().stream().map(SparePart::getMaterialId).toList());
        return page.map(s -> SparePartDto.from(s, onHand.get(s.getMaterialId())));
    }

    @Transactional(readOnly = true)
    public SparePartDto get(Long id) {
        SparePart s = require(id);
        return SparePartDto.from(s, inventoryQuery.availableQtyByMaterial(List.of(s.getMaterialId()))
                .get(s.getMaterialId()));
    }

    @Transactional
    public SparePartDto create(SaveSparePartRequest req) {
        if (partRepo.existsBySparePartCode(req.sparePartCode())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Spare part code already exists: " + req.sparePartCode());
        }
        validateStockLevels(req);
        MaterialDto material = materialService.create(materialRequest(req));
        SparePart s = new SparePart();
        s.setSparePartCode(req.sparePartCode());
        s.setMaterialId(material.id());
        apply(s, req);
        s = partRepo.save(s);
        auditService.record("SPARE_PART_CREATE", "SparePart", String.valueOf(s.getId()), null, s.getSparePartCode(),
                null);
        return get(s.getId());
    }

    @Transactional
    public SparePartDto update(Long id, SaveSparePartRequest req) {
        SparePart s = require(id);
        if (!s.getSparePartCode().equals(req.sparePartCode())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A spare part's code cannot be changed (" + s.getSparePartCode() + ")");
        }
        validateStockLevels(req);
        String old = s.getName() + " min=" + s.getMinStock() + " reorder=" + s.getReorderLevel();
        apply(s, req);
        s = partRepo.save(s);
        materialService.update(s.getMaterialId(), materialRequest(req));
        auditService.record("SPARE_PART_UPDATE", "SparePart", String.valueOf(id), old,
                s.getName() + " min=" + s.getMinStock() + " reorder=" + s.getReorderLevel(), null);
        return get(id);
    }

    /** A deactivated part can no longer be issued; its material and stock are untouched. */
    @Transactional
    public SparePartDto deactivate(Long id) {
        SparePart s = require(id);
        if (s.isActive()) {
            s.setActive(false);
            partRepo.save(s);
            auditService.record("SPARE_PART_DEACTIVATE", "SparePart", String.valueOf(id), "active", "inactive", null);
        }
        return get(id);
    }

    @Transactional
    public SparePartIssueLineDto issue(Long requestId, IssueSparePartRequest req) {
        MaintenanceRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new NotFoundException("MaintenanceRequest", requestId));
        if (!MaintenanceStatus.parse(request.getStatus()).acceptsWork()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Spare parts cannot be issued to " + request.getRequestNumber() + " because it is "
                            + request.getStatus());
        }
        SparePart part = require(req.sparePartId());
        if (!part.isActive()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Spare part " + part.getSparePartCode() + " is inactive and cannot be issued");
        }
        BigDecimal quantity = req.quantity().setScale(3, RoundingMode.HALF_UP);
        if (quantity.signum() <= 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Quantity must be positive");
        }
        List<Allocation> allocations = allocator.allocate(part.getMaterialId(), quantity, req.warehouseId(), null);

        SparePartIssueLine line = new SparePartIssueLine();
        line.setMaintenanceRequestId(requestId);
        line.setSparePartId(part.getId());
        line.setWarehouseId(req.warehouseId());
        line.setIssuedQty(quantity);
        line.setIssuedBy(currentUserId());
        line.setIssuedAt(Instant.now());
        line.setRemarks(req.remarks());
        line = issueRepo.save(line);

        BigDecimal cost = BigDecimal.ZERO;
        for (Allocation a : allocations) {
            PostingResult posted = postingService.post(new PostingCommand(
                    "MAINTENANCE_ISSUE", "MATERIAL", part.getMaterialId(), null, a.batchId(), null,
                    a.warehouseId(), a.locationId(), "AVAILABLE", a.quantity(), part.getUom(), a.unitCost(),
                    ISSUE_REFERENCE, line.getId(), request.getRequestNumber(), req.remarks()));
            cost = cost.add(a.quantity().multiply(posted.unitCost()));
        }
        line.setUnitCost(cost.divide(quantity, 4, RoundingMode.HALF_UP));
        line = issueRepo.save(line);
        auditService.record("SPARE_PART_ISSUE", "MaintenanceRequest", String.valueOf(requestId), null,
                quantity.toPlainString() + " x " + part.getSparePartCode(), req.remarks());
        return SparePartIssueLineDto.from(line, part);
    }

    @Transactional(readOnly = true)
    public List<SparePartIssueLineDto> issuesOf(Long requestId) {
        if (!requestRepo.existsById(requestId)) {
            throw new NotFoundException("MaintenanceRequest", requestId);
        }
        List<SparePartIssueLine> lines = issueRepo.findByMaintenanceRequestIdOrderByIdAsc(requestId);
        Map<Long, SparePart> parts = partRepo.findAllById(lines.stream().map(SparePartIssueLine::getSparePartId)
                .distinct().toList()).stream().collect(Collectors.toMap(SparePart::getId, Function.identity()));
        return lines.stream().map(l -> SparePartIssueLineDto.from(l, parts.get(l.getSparePartId()))).toList();
    }

    private void apply(SparePart s, SaveSparePartRequest req) {
        s.setName(req.name());
        s.setDescription(req.description());
        s.setUom(req.uom());
        s.setCompatibleMachineTypes(req.compatibleMachineTypes());
        s.setMinStock(req.minStock());
        s.setReorderLevel(req.reorderLevel());
        s.setStandardCost(req.standardCost());
    }

    private SaveMaterialRequest materialRequest(SaveSparePartRequest req) {
        return new SaveMaterialRequest(req.sparePartCode(), req.name(), MATERIAL_TYPE, null, null, req.uom(), null,
                req.minStock(), req.reorderLevel(), null, req.standardCost(), null, req.description(), null);
    }

    private void validateStockLevels(SaveSparePartRequest req) {
        if (req.minStock().compareTo(req.reorderLevel()) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Minimum stock must not exceed reorder level");
        }
    }

    private SparePart require(Long id) {
        return partRepo.findById(id).orElseThrow(() -> new NotFoundException("SparePart", id));
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) {
            return u.id();
        }
        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                "Issuing a spare part needs an authenticated user to attribute it to");
    }
}
