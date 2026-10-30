package com.mfg.ims.procurement.service;

import java.math.BigDecimal;
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
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.inventory.domain.MaterialBatch;
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.service.BatchService;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.procurement.domain.GoodsReceipt;
import com.mfg.ims.procurement.domain.GoodsReceiptItem;
import com.mfg.ims.enterprise.scope.PlantScope;
import com.mfg.ims.procurement.domain.PurchaseOrder;
import com.mfg.ims.procurement.domain.PurchaseOrderItem;
import com.mfg.ims.procurement.dto.CreateGrRequest;
import com.mfg.ims.procurement.dto.GoodsReceiptDto;
import com.mfg.ims.procurement.event.GoodsReceivedForInspectionEvent;
import com.mfg.ims.procurement.repository.GoodsReceiptItemRepository;
import com.mfg.ims.procurement.repository.GoodsReceiptRepository;
import com.mfg.ims.procurement.repository.PurchaseOrderItemRepository;
import com.mfg.ims.procurement.repository.PurchaseOrderRepository;

@Service
public class GoodsReceiptService {

    private static final String OVER_RECEIPT_TOLERANCE_KEY = "procurement.over_receipt_tolerance_pct";

    private final GoodsReceiptRepository grRepo;
    private final GoodsReceiptItemRepository grItemRepo;
    private final PurchaseOrderRepository poRepo;
    private final PurchaseOrderItemRepository poItemRepo;
    private final InventoryPostingService postingService;
    private final BatchService batchService;
    private final PurchaseOrderService poService;
    private final DocumentNumberService numberService;

    private final com.mfg.ims.material.service.UomConversionService uomConversions;
    private final SystemSettingService settingService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    private final com.mfg.ims.enterprise.scope.PlantScope plantScope;


    public GoodsReceiptService(
            GoodsReceiptRepository grRepo,
            GoodsReceiptItemRepository grItemRepo,
            PurchaseOrderRepository poRepo,
            PurchaseOrderItemRepository poItemRepo,
            InventoryPostingService postingService,
            BatchService batchService,
            PurchaseOrderService poService,
            DocumentNumberService numberService,
            SystemSettingService settingService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,

            com.mfg.ims.enterprise.scope.PlantScope plantScope,
            com.mfg.ims.material.service.UomConversionService uomConversions) {
        this.uomConversions = uomConversions;

        this.plantScope = plantScope;
        this.grRepo = grRepo;
        this.grItemRepo = grItemRepo;
        this.poRepo = poRepo;
        this.poItemRepo = poItemRepo;
        this.postingService = postingService;
        this.batchService = batchService;
        this.poService = poService;
        this.numberService = numberService;
        this.settingService = settingService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public GoodsReceiptDto receive(CreateGrRequest req) {
        PurchaseOrder po = poRepo.findById(req.purchaseOrderId())
                .orElseThrow(() -> new NotFoundException("Purchase order not found: " + req.purchaseOrderId()));
        plantScope.assertVisible(po.getPlantId(), "Purchase order", req.purchaseOrderId());
        Long warehousePlant = plantScope.warehousePlantId(req.warehouseId());
        if (po.getPlantId() != null && warehousePlant != null && !po.getPlantId().equals(warehousePlant)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "The warehouse belongs to a different plant than the purchase order");
        }

        if (!List.of("APPROVED", "SENT_TO_SUPPLIER", "PARTIALLY_RECEIVED").contains(po.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_PO_TRANSITION,
                    "Cannot receive against PO in status: " + po.getStatus());
        }

        BigDecimal tolerancePct = settingService.getDecimal(OVER_RECEIPT_TOLERANCE_KEY, new BigDecimal("10"));

        boolean approvalRequired = "REQUIRED".equalsIgnoreCase(
                settingService.get("inventory.inward.approval_mode", "NONE"));

        GoodsReceipt gr = new GoodsReceipt();
        gr.setGrNumber(numberService.next("GR"));
        gr.setPurchaseOrderId(req.purchaseOrderId());
        gr.setReceiptDate(req.receiptDate() != null ? req.receiptDate() : LocalDate.now());
        gr.setSupplierChallanNo(req.supplierChallanNo());
        gr.setVehicleNo(req.vehicleNo());
        gr.setWarehouseId(req.warehouseId());
        gr.setPlantId(po.getPlantId());
        gr.setStatus(approvalRequired ? "PENDING_APPROVAL" : "POSTED");
        gr.setRequiresApproval(approvalRequired);
        gr.setReceivedBy(currentUserId());
        gr = grRepo.save(gr);

        List<GoodsReceiptItem> savedItems = new ArrayList<>();

        for (CreateGrRequest.GrLineRequest line : req.items()) {
            PurchaseOrderItem poItem = poItemRepo.lockById(line.poItemId())
                    .orElseThrow(() -> new NotFoundException("PO item not found: " + line.poItemId()));

            // MDM-003 / GC-1: a quantity entered in an alternative unit is converted to the material base unit
            BigDecimal receivedQty = uomConversions.toBase(poItem.getMaterialId(), line.receivedQty(), line.uom());

            // Over-receipt check (with tolerance)
            BigDecimal maxAllowed = poItem.getOrderedQty()
                    .multiply(BigDecimal.ONE.add(tolerancePct.divide(BigDecimal.valueOf(100))))
                    .subtract(poItem.getReceivedQty());
            if (receivedQty.compareTo(maxAllowed) > 0) {
                throw new BusinessRuleException(ErrorCode.OVER_RECEIPT,
                        String.format("Line %d: received %.3f exceeds remaining allowed %.3f",
                                poItem.getLineNo(), receivedQty, maxAllowed));
            }

            boolean inspectionRequired = line.inspectionRequired() == null || line.inspectionRequired();

            // Create and persist GR item first (get its ID)
            GoodsReceiptItem grItem = new GoodsReceiptItem();
            grItem.setGoodsReceipt(gr);
            grItem.setPoItemId(line.poItemId());
            grItem.setMaterialId(poItem.getMaterialId());
            grItem.setReceivedQty(receivedQty);
            grItem.setLocationId(line.locationId());
            grItem.setHeatNumber(line.heatNumber());
            grItem.setCertificateNumber(line.certificateNumber());
            grItem.setManufacturingDate(line.manufacturingDate());
            grItem.setInspectionRequired(inspectionRequired);
            grItem = grItemRepo.save(grItem);

            // Create material batch (enters as QUARANTINE)
            MaterialBatch batch = batchService.createMaterialBatch(
                    poItem.getMaterialId(), po.getSupplierId(),
                    po.getId(), grItem.getId(),
                    line.manufacturingDate(), LocalDate.now(),
                    null, receivedQty,
                    line.heatNumber(), line.certificateNumber(),
                    poItem.getRate(), "QUARANTINE");

            grItem.setMaterialBatchId(batch.getId());
            grItem = grItemRepo.save(grItem);

            if (!approvalRequired) {
                // Post to inventory as QUARANTINE
                postingService.post(new PostingCommand(
                        "PURCHASE_RECEIPT", "MATERIAL",
                        poItem.getMaterialId(), null,
                        batch.getId(), null,
                        req.warehouseId(), line.locationId(),
                        "QUARANTINE",
                        receivedQty, null,
                        poItem.getRate(),
                        "GOODS_RECEIPT", gr.getId(), gr.getGrNumber(),
                        "GR: " + gr.getGrNumber()));

                // Update po_item received qty
                poItem.setReceivedQty(poItem.getReceivedQty().add(receivedQty));
                poItemRepo.save(poItem);

                // Publish event — InspectionService listener creates the inspection
                eventPublisher.publishEvent(new GoodsReceivedForInspectionEvent(
                        grItem.getId(), batch.getId(), poItem.getMaterialId(),
                        req.warehouseId(), line.locationId(), inspectionRequired));
            }

            savedItems.add(grItem);
        }

        if (!approvalRequired) {
            poService.refreshPoStatus(po.getId());
        }

        auditService.record(approvalRequired ? "GR_PENDING_APPROVAL" : "GR_POSTED",
                "GOODS_RECEIPT", String.valueOf(gr.getId()),
                null, gr.getGrNumber(), null);

        return toDto(gr, savedItems);
    }

    @Transactional(readOnly = true)
    public GoodsReceiptDto findById(Long id) {
        GoodsReceipt gr = grRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Goods receipt not found: " + id));
        plantScope.assertVisible(gr.getPlantId(), "Goods receipt", id);
        return toDto(gr, grItemRepo.findByGoodsReceiptId(id));
    }

    @Transactional(readOnly = true)
    public Page<GoodsReceiptDto> search(String status, Long purchaseOrderId, String poNumber,
                                        java.time.LocalDate from, java.time.LocalDate to, Pageable pageable) {
        PlantScope.Filter scope = plantScope.filter();
        return grRepo.searchInPlants(status, purchaseOrderId, poNumber, from, to, scope.scoped(), scope.plantIds(), pageable)
                .map(gr -> toDto(gr, grItemRepo.findByGoodsReceiptId(gr.getId())));
    }

    @Transactional(readOnly = true)
    public Page<GoodsReceiptDto> findByPurchaseOrder(Long poId, Pageable pageable) {
        PurchaseOrder po = poRepo.findById(poId)
                .orElseThrow(() -> new NotFoundException("Purchase order not found: " + poId));
        plantScope.assertVisible(po.getPlantId(), "Purchase order", poId);
        return grRepo.findByPurchaseOrderId(poId, pageable)
                .map(gr -> toDto(gr, grItemRepo.findByGoodsReceiptId(gr.getId())));
    }

    @Transactional
    public GoodsReceiptDto approve(Long id) {
        GoodsReceipt gr = grRepo.findById(id)
                .orElseThrow(() -> new com.mfg.ims.common.exception.NotFoundException("Goods receipt not found: " + id));
        if (!"PENDING_APPROVAL".equals(gr.getStatus())) {
            throw new com.mfg.ims.common.exception.BusinessRuleException(
                    com.mfg.ims.common.exception.ErrorCode.VALIDATION_FAILED,
                    "Goods receipt is not pending approval");
        }
        List<GoodsReceiptItem> items = grItemRepo.findByGoodsReceiptId(id);
        for (GoodsReceiptItem grItem : items) {
            PurchaseOrderItem poItem = poItemRepo.findById(grItem.getPoItemId())
                    .orElseThrow(() -> new com.mfg.ims.common.exception.NotFoundException(
                            "PO item not found: " + grItem.getPoItemId()));
            postingService.post(new PostingCommand(
                    "PURCHASE_RECEIPT", "MATERIAL",
                    grItem.getMaterialId(), null,
                    grItem.getMaterialBatchId(), null,
                    gr.getWarehouseId(), grItem.getLocationId(),
                    "QUARANTINE",
                    grItem.getReceivedQty(), null,
                    poItem.getRate(),
                    "GOODS_RECEIPT", gr.getId(), gr.getGrNumber(),
                    "GR: " + gr.getGrNumber()));
            poItem.setReceivedQty(poItem.getReceivedQty().add(grItem.getReceivedQty()));
            poItemRepo.save(poItem);
            eventPublisher.publishEvent(new GoodsReceivedForInspectionEvent(
                    grItem.getId(), grItem.getMaterialBatchId(), grItem.getMaterialId(),
                    gr.getWarehouseId(), grItem.getLocationId(), grItem.isInspectionRequired()));
        }
        gr.setStatus("POSTED");
        grRepo.save(gr);
        poService.refreshPoStatus(gr.getPurchaseOrderId());
        auditService.record("GR_APPROVED", "GOODS_RECEIPT", String.valueOf(gr.getId()),
                "PENDING_APPROVAL", "POSTED", null);
        return toDto(gr, items);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.mfg.ims.auth.service.UserPermissionCache.CachedUser u) {
                return u.id();
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    private GoodsReceiptDto toDto(GoodsReceipt gr, List<GoodsReceiptItem> items) {
        List<GoodsReceiptDto.GrItemDto> itemDtos = (items == null ? List.<GoodsReceiptItem>of() : items).stream()
                .map(i -> new GoodsReceiptDto.GrItemDto(
                        i.getId(), i.getPoItemId(), i.getMaterialId(), null,
                        i.getReceivedQty(), i.getLocationId(),
                        i.getHeatNumber(), i.getCertificateNumber(),
                        i.getMaterialBatchId(), null,
                        i.isInspectionRequired(), null))
                .toList();
        String poNumber = gr.getPurchaseOrderId() != null
                ? poRepo.findById(gr.getPurchaseOrderId()).map(PurchaseOrder::getPoNumber).orElse(null)
                : null;
        return new GoodsReceiptDto(
                gr.getId(), gr.getGrNumber(), gr.getPurchaseOrderId(),
                poNumber,
                gr.getReceiptDate(), gr.getSupplierChallanNo(), gr.getVehicleNo(),
                gr.getWarehouseId(), gr.getStatus(), gr.getReceivedBy(), itemDtos,
                gr.getServiceConfirmationId(), gr.isRequiresApproval());
    }
}
