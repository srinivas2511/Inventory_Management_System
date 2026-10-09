package com.springmfg.ims.production.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

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
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.repository.MaterialBatchRepository;
import com.springmfg.ims.inventory.service.BatchAllocator;
import com.springmfg.ims.inventory.service.BatchAllocator.Allocation;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.production.domain.MaterialIssue;
import com.springmfg.ims.production.domain.MaterialIssueLine;
import com.springmfg.ims.production.domain.ProductionOrder;
import com.springmfg.ims.production.domain.ProductionOrderMaterial;
import com.springmfg.ims.production.dto.CreateIssueRequest;
import com.springmfg.ims.production.dto.IssueDto;
import com.springmfg.ims.production.repository.MaterialIssueRepository;
import com.springmfg.ims.production.repository.ProductionOrderMaterialRepository;
import com.springmfg.ims.production.repository.ProductionOrderRepository;
import com.springmfg.ims.warehouse.domain.WarehouseLocation;
import com.springmfg.ims.warehouse.repository.WarehouseLocationRepository;

@Service
public class MaterialIssueService {

    private final MaterialIssueRepository issueRepo;
    private final ProductionOrderRepository orderRepo;
    private final ProductionOrderMaterialRepository pomRepo;
    private final BatchAllocator allocator;
    private final InventoryPostingService postingService;
    private final MaterialBatchRepository batchRepo;
    private final WarehouseLocationRepository locationRepo;
    private final DocumentNumberService numberService;

    private final com.springmfg.ims.material.service.UomConversionService uomConversions;
    private final SystemSettingService settingService;
    private final AuditService auditService;
    private final ProductionOrderService orderService;

    private final com.springmfg.ims.enterprise.scope.PlantScope plantScope;


    public MaterialIssueService(
            MaterialIssueRepository issueRepo,
            ProductionOrderRepository orderRepo,
            ProductionOrderMaterialRepository pomRepo,
            BatchAllocator allocator,
            InventoryPostingService postingService,
            MaterialBatchRepository batchRepo,
            WarehouseLocationRepository locationRepo,
            DocumentNumberService numberService,
            SystemSettingService settingService,
            AuditService auditService,
            ProductionOrderService orderService,

            com.springmfg.ims.enterprise.scope.PlantScope plantScope,
            com.springmfg.ims.material.service.UomConversionService uomConversions) {
        this.uomConversions = uomConversions;

        this.plantScope = plantScope;
        this.issueRepo = issueRepo;
        this.orderRepo = orderRepo;
        this.pomRepo = pomRepo;
        this.allocator = allocator;
        this.postingService = postingService;
        this.batchRepo = batchRepo;
        this.locationRepo = locationRepo;
        this.numberService = numberService;
        this.settingService = settingService;
        this.auditService = auditService;
        this.orderService = orderService;
    }

    @Transactional
    public IssueDto issue(CreateIssueRequest req) {
        ProductionOrder order = orderRepo.findById(req.productionOrderId())
                .orElseThrow(() -> new NotFoundException("Production order not found: " + req.productionOrderId()));
        plantScope.assertVisible(order.getPlantId(), "Production order", req.productionOrderId());

        if (!List.of("RELEASED", "IN_PROGRESS").contains(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot issue material to order in status: " + order.getStatus());
        }

        // Find WIP location in the warehouse
        List<WarehouseLocation> wipLocs = locationRepo.findByWarehouseIdAndActiveTrue(req.warehouseId())
                .stream()
                .filter(l -> "WIP".equals(l.getLocationType()))
                .toList();
        if (wipLocs.isEmpty()) {
            throw new NotFoundException("No active WIP location in warehouse: " + req.warehouseId());
        }
        WarehouseLocation wipLoc = wipLocs.get(0);

        BigDecimal tolerancePct = settingService.getDecimal("production.issue.tolerance_pct", BigDecimal.TEN);

        MaterialIssue issue = new MaterialIssue();
        issue.setIssueNumber(numberService.next("MI"));
        issue.setProductionOrderId(req.productionOrderId());
        issue.setFromWarehouseId(req.warehouseId());
        issue.setIssuedBy(currentUserId());
        issue.setRemarks(req.remarks());
        issue = issueRepo.save(issue);

        List<IssueDto.IssueLineDto> lineDtos = new ArrayList<>();
        boolean planned = pomRepo.existsByProductionOrderId(req.productionOrderId());

        for (CreateIssueRequest.IssueLineRequest lineReq : req.lines()) {
            // MDM-003 / GC-1: a quantity entered in an alternative unit is converted to the material base unit
            BigDecimal quantity = uomConversions.toBase(lineReq.materialId(), lineReq.quantity(), lineReq.uom());

            // Check tolerance
            ProductionOrderMaterial pom = pomRepo
                    .findByProductionOrderIdAndMaterialId(req.productionOrderId(), lineReq.materialId())
                    .orElse(null);
            if (pom == null && planned) {
                // The order has a material plan (the BOM snapshot taken at release) and this material is not in it:
                // DESIGN 4.6 allows nothing above plan without an approved material request.
                throw new BusinessRuleException(ErrorCode.ISSUE_EXCEEDS_PLAN,
                        "Material " + lineReq.materialId() + " is not part of the plan for this production order");
            }
            if (pom != null) {
                BigDecimal maxIssue = pom.getPlannedQty()
                        .multiply(BigDecimal.ONE.add(tolerancePct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)));
                BigDecimal totalAfter = pom.getIssuedQty().add(quantity);
                if (totalAfter.compareTo(maxIssue) > 0) {
                    throw new BusinessRuleException(ErrorCode.ISSUE_EXCEEDS_PLAN,
                            "Issue would exceed planned + tolerance for material " + lineReq.materialId());
                }
            }

            // Allocate batches (FEFO)
            List<Allocation> allocations = allocator.allocate(
                    lineReq.materialId(), quantity, req.warehouseId(), lineReq.explicitBatchId());

            for (Allocation alloc : allocations) {
                // Post MATERIAL_ISSUE: OUT from RM, IN to WIP
                postingService.post(new PostingCommand(
                        "MATERIAL_ISSUE", "MATERIAL",
                        lineReq.materialId(), null, alloc.batchId(), null,
                        alloc.warehouseId(), alloc.locationId(), "AVAILABLE",
                        alloc.quantity(), null, alloc.unitCost(),
                        "MATERIAL_ISSUE", issue.getId(), issue.getIssueNumber(), req.remarks()));

                postingService.post(new PostingCommand(
                        "MATERIAL_RECEIPT", "MATERIAL",
                        lineReq.materialId(), null, alloc.batchId(), null,
                        req.warehouseId(), wipLoc.getId(), "WIP",
                        alloc.quantity(), null, alloc.unitCost(),
                        "MATERIAL_ISSUE", issue.getId(), issue.getIssueNumber(), req.remarks()));

                MaterialIssueLine line = new MaterialIssueLine();
                line.setMaterialIssue(issue);
                line.setMaterialId(lineReq.materialId());
                line.setMaterialBatchId(alloc.batchId());
                line.setFromLocationId(alloc.locationId());
                line.setToLocationId(wipLoc.getId());
                line.setQuantity(alloc.quantity());
                line.setUnitCost(alloc.unitCost() != null ? alloc.unitCost() : BigDecimal.ZERO);
                issue.getLines().add(line);

                String batchNumber = batchRepo.findById(alloc.batchId())
                        .map(b -> b.getBatchNumber()).orElse(null);
                lineDtos.add(new IssueDto.IssueLineDto(lineReq.materialId(), alloc.batchId(),
                        batchNumber, alloc.locationId(), alloc.quantity(), alloc.unitCost()));
            }

            // Update issued qty
            if (pom != null) {
                pom.setIssuedQty(pom.getIssuedQty().add(quantity));
                pomRepo.save(pom);
            }
        }

        issue = issueRepo.save(issue);
        orderService.moveToInProgressIfNeeded(req.productionOrderId());

        auditService.record("MATERIAL_ISSUE", "MaterialIssue", String.valueOf(issue.getId()),
                null, issue.getIssueNumber(), null);

        return new IssueDto(issue.getId(), issue.getIssueNumber(), req.productionOrderId(), lineDtos);
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return null;
    }
}
