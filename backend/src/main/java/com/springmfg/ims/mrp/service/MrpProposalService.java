package com.springmfg.ims.mrp.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.engineering.bom.Bom;
import com.springmfg.ims.engineering.bom.BomRepository;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.mrp.domain.MrpProposal;
import com.springmfg.ims.mrp.dto.ConvertAllResultDto;
import com.springmfg.ims.mrp.dto.MrpProposalDto;
import com.springmfg.ims.mrp.repository.MrpProposalRepository;
import com.springmfg.ims.procurement.dto.CreateRequisitionRequest;
import com.springmfg.ims.procurement.dto.PurchaseRequisitionDto;
import com.springmfg.ims.procurement.service.PurchaseRequisitionService;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.production.dto.CreateProductionOrderRequest;
import com.springmfg.ims.production.dto.ProductionOrderDto;
import com.springmfg.ims.production.service.ProductionOrderService;

import jakarta.persistence.criteria.Predicate;

/** Review, filter, convert and discard MRP proposals. Conversion is always a deliberate human action (rule 20). */
@Service
public class MrpProposalService {

    private static final String PURCHASE = "PURCHASE";
    private static final String PRODUCTION = "PRODUCTION";

    private final MrpProposalRepository proposalRepo;
    private final MaterialRepository materialRepo;
    private final ProductRepository productRepo;
    private final BomRepository bomRepo;
    private final PurchaseRequisitionService requisitionService;
    private final ProductionOrderService productionOrderService;
    private final AuditService auditService;

    public MrpProposalService(MrpProposalRepository proposalRepo, MaterialRepository materialRepo,
                              ProductRepository productRepo, BomRepository bomRepo,
                              PurchaseRequisitionService requisitionService,
                              ProductionOrderService productionOrderService, AuditService auditService) {
        this.proposalRepo = proposalRepo;
        this.materialRepo = materialRepo;
        this.productRepo = productRepo;
        this.bomRepo = bomRepo;
        this.requisitionService = requisitionService;
        this.productionOrderService = productionOrderService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<MrpProposalDto> search(Long runId, String type, String status, Long materialId, Long productId,
                                       Boolean capacityOverload, Pageable pageable) {
        Specification<MrpProposal> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (runId != null) p.add(cb.equal(root.get("mrpRunId"), runId));
            if (type != null) p.add(cb.equal(root.get("proposalType"), type));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (materialId != null) p.add(cb.equal(root.get("materialId"), materialId));
            if (productId != null) p.add(cb.equal(root.get("productId"), productId));
            if (capacityOverload != null) p.add(cb.equal(root.get("capacityOverload"), capacityOverload));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<MrpProposal> page = proposalRepo.findAll(spec, pageable);
        Names names = names(page.getContent());
        return page.map(p -> toDto(p, names));
    }

    @Transactional(readOnly = true)
    public MrpProposalDto findById(Long id) {
        MrpProposal p = require(id);
        return toDto(p, names(List.of(p)));
    }

    @Transactional
    public MrpProposalDto convertToRequisition(Long id) {
        MrpProposal p = requireOpen(id, PURCHASE);
        convertPurchase(p);
        return toDto(p, names(List.of(p)));
    }

    @Transactional
    public MrpProposalDto convertToProductionOrder(Long id) {
        MrpProposal p = requireOpen(id, PRODUCTION);
        convertProduction(p);
        return toDto(p, names(List.of(p)));
    }

    /** All-or-nothing: one failing proposal rolls the whole batch back. */
    @Transactional
    public ConvertAllResultDto convertAll(Long runId, String type) {
        if (!PURCHASE.equals(type) && !PRODUCTION.equals(type)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "type must be PURCHASE or PRODUCTION");
        }
        List<MrpProposal> open = proposalRepo.findByMrpRunIdAndProposalTypeAndStatusOrderById(runId, type, "OPEN");
        List<Long> converted = new ArrayList<>();
        for (MrpProposal p : open) {
            if (PURCHASE.equals(type)) {
                convertPurchase(p);
            } else {
                convertProduction(p);
            }
            converted.add(p.getId());
        }
        return new ConvertAllResultDto(converted.size(), converted);
    }

    @Transactional
    public MrpProposalDto discard(Long id, String reason) {
        MrpProposal p = proposalRepo.findById(id).orElseThrow(() -> notFound(id));
        assertOpen(p);
        p.setStatus("DISCARDED");
        p.setDiscardReason(reason);
        p = proposalRepo.save(p);
        auditService.record("MRP_PROPOSAL_DISCARD", "MrpProposal", String.valueOf(id), "OPEN", "DISCARDED", reason);
        return toDto(p, names(List.of(p)));
    }

    private void convertPurchase(MrpProposal p) {
        PurchaseRequisitionDto pr = requisitionService.create(new CreateRequisitionRequest(
                p.getMaterialId(), p.getProposedQty(), p.getUom(), p.getNeedDate(),
                "MRP proposal " + p.getId() + " (run " + p.getMrpRunId() + ")"));
        markConverted(p, "PR", pr.id());
    }

    private void convertProduction(MrpProposal p) {
        Bom bom = bomRepo.findById(p.getBomId())
                .orElseThrow(() -> new NotFoundException("BOM not found: " + p.getBomId()));
        if (!"ACTIVE".equals(bom.getStatus())) {
            throw new BusinessRuleException(ErrorCode.BOM_NOT_ACTIVE,
                    "BOM " + bom.getBomNumber() + " is no longer ACTIVE; rerun MRP before converting proposal " + p.getId());
        }
        ProductionOrderDto order = productionOrderService.create(new CreateProductionOrderRequest(
                p.getProductId(), p.getBomId(), p.getProposedQty(), p.getProposedOrderDate(), p.getNeedDate(),
                null, null, null, null, null, "MRP proposal " + p.getId() + " (run " + p.getMrpRunId() + ")"));
        markConverted(p, "PRODUCTION_ORDER", order.id());
    }

    private void markConverted(MrpProposal p, String documentType, Long documentId) {
        p.setStatus("CONVERTED");
        p.setConvertedDocumentType(documentType);
        p.setConvertedDocumentId(documentId);
        p.setConvertedBy(CurrentUser.id());
        p.setConvertedAt(Instant.now());
        proposalRepo.save(p);
        auditService.record("MRP_PROPOSAL_CONVERT", "MrpProposal", String.valueOf(p.getId()), "OPEN",
                "CONVERTED " + documentType + " " + documentId, null);
    }

    private MrpProposal require(Long id) {
        return proposalRepo.findById(id).orElseThrow(() -> notFound(id));
    }

    private MrpProposal requireOpen(Long id, String expectedType) {
        MrpProposal p = require(id);
        assertOpen(p);
        if (!expectedType.equals(p.getProposalType())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Proposal " + id + " is a " + p.getProposalType() + " proposal, not " + expectedType);
        }
        return p;
    }

    private void assertOpen(MrpProposal p) {
        if (!"OPEN".equals(p.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Proposal " + p.getId() + " is " + p.getStatus() + "; only OPEN proposals can be actioned");
        }
    }

    private static NotFoundException notFound(Long id) {
        return new NotFoundException("MRP proposal not found: " + id);
    }

    private record Names(Map<Long, Material> materials, Map<Long, Product> products) {}

    private Names names(List<MrpProposal> proposals) {
        Set<Long> materialIds = proposals.stream().map(MrpProposal::getMaterialId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> productIds = proposals.stream().map(MrpProposal::getProductId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Material> materials = new HashMap<>();
        materialRepo.findAllById(materialIds).forEach(m -> materials.put(m.getId(), m));
        Map<Long, Product> products = new HashMap<>();
        productRepo.findAllById(productIds).forEach(pr -> products.put(pr.getId(), pr));
        return new Names(materials, products);
    }

    private MrpProposalDto toDto(MrpProposal p, Names names) {
        Material m = p.getMaterialId() == null ? null : names.materials().get(p.getMaterialId());
        Product pr = p.getProductId() == null ? null : names.products().get(p.getProductId());
        return new MrpProposalDto(p.getId(), p.getMrpRunId(), p.getProposalType(),
                p.getMaterialId(), m == null ? null : m.getMaterialCode(), m == null ? null : m.getName(),
                p.getProductId(), pr == null ? null : pr.getProductCode(), pr == null ? null : pr.getName(),
                p.getBomId(), p.getUom(), p.getRequiredQty(), p.getProposedQty(), p.getNeedDate(),
                p.getProposedOrderDate(), p.getLeadTimeDays(), p.isLeadTimeAdjusted(), p.getSourceDemandType(),
                p.getSourceDemandId(), p.getLotSizeRule(), p.getMachineHoursRequired(), p.isCapacityOverload(),
                p.getStatus(), p.getConvertedDocumentType(), p.getConvertedDocumentId(), p.getDiscardReason());
    }
}
