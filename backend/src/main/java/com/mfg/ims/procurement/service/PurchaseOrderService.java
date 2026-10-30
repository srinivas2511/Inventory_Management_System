package com.mfg.ims.procurement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.event.PurchaseOrderApprovedEvent;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.common.security.SegregationOfDuties;
import com.mfg.ims.procurement.domain.PurchaseOrder;
import com.mfg.ims.enterprise.scope.PlantScope;
import com.mfg.ims.procurement.domain.PurchaseOrderItem;
import com.mfg.ims.procurement.domain.PurchaseRequisition;
import com.mfg.ims.procurement.dto.CreatePoRequest;
import com.mfg.ims.procurement.dto.PurchaseOrderDto;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.partner.repository.SupplierRepository;
import com.mfg.ims.procurement.repository.PurchaseOrderItemRepository;
import com.mfg.ims.procurement.repository.PurchaseOrderRepository;
import com.mfg.ims.procurement.repository.PurchaseRequisitionRepository;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository poRepo;
    private final PurchaseOrderItemRepository poItemRepo;
    private final PurchaseRequisitionRepository prRepo;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    private final com.mfg.ims.material.service.SupplierApprovedMaterialService approvedMaterials;

    private final com.mfg.ims.enterprise.scope.PlantScope plantScope;
    private final SupplierRepository supplierRepo;
    private final MaterialRepository materialRepo;


    public PurchaseOrderService(
            PurchaseOrderRepository poRepo,
            PurchaseOrderItemRepository poItemRepo,
            PurchaseRequisitionRepository prRepo,
            DocumentNumberService numberService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,
            SupplierRepository supplierRepo,
            MaterialRepository materialRepo,
            com.mfg.ims.enterprise.scope.PlantScope plantScope,
            com.mfg.ims.material.service.SupplierApprovedMaterialService approvedMaterials) {
        this.approvedMaterials = approvedMaterials;

        this.plantScope = plantScope;
        this.supplierRepo = supplierRepo;
        this.materialRepo = materialRepo;
        this.poRepo = poRepo;
        this.poItemRepo = poItemRepo;
        this.prRepo = prRepo;
        this.numberService = numberService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PurchaseOrderDto create(CreatePoRequest req) {
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber(numberService.next("PUR"));
        po.setSupplierId(req.supplierId());
        po.setPoDate(req.poDate());
        po.setExpectedDeliveryDate(req.expectedDeliveryDate());
        po.setPaymentTerms(req.paymentTerms());
        po.setRemarks(req.remarks());
        po.setRequisitionId(req.requisitionId());
        po.setStatus("DRAFT");
        po.setCurrency("INR");
        po.setPlantId(plantScope.resolveForCreate(req.plantId()));
        po = poRepo.save(po);

        AtomicInteger lineNo = new AtomicInteger(1);
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        for (CreatePoRequest.PoLineRequest line : req.items()) {
            approvedMaterials.assertMaySupply(req.supplierId(), line.materialId()); // MDM-020 / GC-2 (setting-driven)
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(po);
            item.setLineNo(lineNo.getAndIncrement());
            item.setMaterialId(line.materialId());
            item.setOrderedQty(line.orderedQty());
            item.setReceivedQty(BigDecimal.ZERO);
            item.setAcceptedQty(BigDecimal.ZERO);
            item.setRejectedQty(BigDecimal.ZERO);
            item.setRate(line.rate());
            BigDecimal taxPct = line.taxPct() != null ? line.taxPct() : BigDecimal.ZERO;
            item.setTaxPct(taxPct);
            BigDecimal base = line.orderedQty().multiply(line.rate()).setScale(4, RoundingMode.HALF_UP);
            BigDecimal tax  = base.multiply(taxPct).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            item.setLineTotal(base.add(tax));
            po.getItems().add(poItemRepo.save(item));
            subtotal = subtotal.add(base);
            taxTotal = taxTotal.add(tax);
        }

        po.setSubtotal(subtotal);
        po.setTaxTotal(taxTotal);
        po.setGrandTotal(subtotal.add(taxTotal));
        po = poRepo.save(po);

        // Link requisition
        if (req.requisitionId() != null) {
            final Long savedPoId = po.getId();
            prRepo.findById(req.requisitionId()).ifPresent(pr -> {
                pr.setStatus("CONVERTED");
                pr.setConvertedPoId(savedPoId);
                prRepo.save(pr);
            });
        }

        auditService.record("PO_CREATED", "PURCHASE_ORDER", String.valueOf(po.getId()),
                null, po.getPoNumber(), null);
        return toDto(po);
    }

    @Transactional
    public PurchaseOrderDto submit(Long id) {
        PurchaseOrder po = requirePo(id);
        requireState(po, "DRAFT", "SUBMITTED");
        Long userId = currentUserId();
        po.setStatus("SUBMITTED");
        po.setSubmittedBy(userId);
        po = poRepo.save(po);
        auditService.record("PO_SUBMITTED", "PURCHASE_ORDER", String.valueOf(id),
                "DRAFT", "SUBMITTED", null);
        return toDto(po);
    }

    @Transactional
    public PurchaseOrderDto approve(Long id) {
        PurchaseOrder po = requirePo(id);
        requireState(po, "SUBMITTED", "APPROVED");
        Long userId = currentUserId();
        // Segregation of duties: approver != submitter
        SegregationOfDuties.requireDifferentUser(userId, po.getSubmittedBy(),
                "Requester cannot approve own purchase order");
        po.setStatus("APPROVED");
        po.setApprovedBy(userId);
        po.setApprovedAt(Instant.now());
        po = poRepo.save(po);
        auditService.record("PO_APPROVED", "PURCHASE_ORDER", String.valueOf(id),
                "SUBMITTED", "APPROVED", null);
        eventPublisher.publishEvent(new PurchaseOrderApprovedEvent(po.getId()));
        return toDto(po);
    }

    @Transactional
    public PurchaseOrderDto sendToSupplier(Long id) {
        PurchaseOrder po = requirePo(id);
        requireState(po, "APPROVED", "SENT_TO_SUPPLIER");
        po.setStatus("SENT_TO_SUPPLIER");
        po = poRepo.save(po);
        auditService.record("PO_SENT", "PURCHASE_ORDER", String.valueOf(id),
                "APPROVED", "SENT_TO_SUPPLIER", null);
        return toDto(po);
    }

    @Transactional
    public PurchaseOrderDto cancel(Long id, String reason) {
        PurchaseOrder po = requirePo(id);
        if (List.of("PARTIALLY_RECEIVED", "RECEIVED").contains(po.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_PO_TRANSITION,
                    "Cannot cancel a PO that has received goods");
        }
        String prev = po.getStatus();
        po.setStatus("CANCELLED");
        po = poRepo.save(po);
        auditService.record("PO_CANCELLED", "PURCHASE_ORDER", String.valueOf(id),
                prev, "CANCELLED", reason);
        return toDto(po);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDto findById(Long id) {
        return toDto(requirePo(id));
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderDto> search(String status, Long supplierId, Pageable pageable) {
        PlantScope.Filter scope = plantScope.filter();
        return poRepo.searchInPlants(status, supplierId, scope.scoped(), scope.plantIds(), pageable).map(this::toDto);
    }

    // Called by GoodsReceiptService after posting receipts
    @Transactional
    public void refreshPoStatus(Long poId) {
        PurchaseOrder po = requirePo(poId);
        List<PurchaseOrderItem> items = poItemRepo.findByPurchaseOrderId(poId);
        boolean allReceived = items.stream()
                .allMatch(i -> i.getReceivedQty().compareTo(i.getOrderedQty()) >= 0);
        boolean anyReceived = items.stream()
                .anyMatch(i -> i.getReceivedQty().compareTo(BigDecimal.ZERO) > 0);
        if (allReceived) {
            po.setStatus("RECEIVED");
        } else if (anyReceived) {
            po.setStatus("PARTIALLY_RECEIVED");
        }
        poRepo.save(po);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private PurchaseOrder requirePo(Long id) {
        PurchaseOrder po = poRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Purchase order not found: " + id));
        plantScope.assertVisible(po.getPlantId(), "Purchase order", id);
        return po;
    }

    private void requireState(PurchaseOrder po, String from, String to) {
        if (!po.getStatus().equals(from)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_PO_TRANSITION,
                    "Cannot transition from " + po.getStatus() + " to " + to);
        }
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.mfg.ims.auth.service.UserPermissionCache.CachedUser u) {
                return u.id();
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    private PurchaseOrderDto toDto(PurchaseOrder po) {
        List<PurchaseOrderItem> items = po.getItems() != null
                ? po.getItems()
                : poItemRepo.findByPurchaseOrderId(po.getId());

        List<PurchaseOrderDto.PurchaseOrderItemDto> itemDtos = items.stream()
                .map(i -> {
                    String[] matNames = materialRepo.findById(i.getMaterialId())
                            .map(m -> new String[]{m.getMaterialCode(), m.getName(), m.getUom()})
                            .orElse(new String[]{null, null, null});
                    return new PurchaseOrderDto.PurchaseOrderItemDto(
                            i.getId(), i.getLineNo(), i.getMaterialId(),
                            matNames[0], matNames[1], matNames[2],
                            i.getOrderedQty(), i.getReceivedQty(), i.getAcceptedQty(), i.getRejectedQty(),
                            i.getRate(), i.getTaxPct(), i.getLineTotal());
                })
                .toList();

        String supplierName = supplierRepo.findById(po.getSupplierId())
                .map(s -> s.getSupplierCode() + " · " + s.getName())
                .orElse(null);
        List<String> allowed = allowedActions(po.getStatus());
        return new PurchaseOrderDto(
                po.getId(), po.getPoNumber(), po.getSupplierId(), supplierName,
                po.getPoDate(), po.getExpectedDeliveryDate(), po.getStatus(),
                po.getSubtotal(), po.getTaxTotal(), po.getGrandTotal(), po.getCurrency(),
                po.getPaymentTerms(), po.getRemarks(), po.getRequisitionId(),
                po.getSubmittedBy(), po.getApprovedBy(), po.getApprovedAt(),
                itemDtos, allowed);
    }

    private List<String> allowedActions(String status) {
        return switch (status) {
            case "DRAFT"              -> List.of("submit", "cancel");
            case "SUBMITTED"          -> List.of("approve", "cancel");
            case "APPROVED"           -> List.of("send_to_supplier", "cancel");
            case "SENT_TO_SUPPLIER"   -> List.of("receive");
            case "PARTIALLY_RECEIVED" -> List.of("receive");
            default                   -> List.of();
        };
    }
}
