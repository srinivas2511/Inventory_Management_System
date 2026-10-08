package com.springmfg.ims.procurement.service;

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

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.inventory.domain.MaterialBatch;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.service.BatchService;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.procurement.domain.GoodsReceipt;
import com.springmfg.ims.procurement.domain.GoodsReceiptItem;
import com.springmfg.ims.enterprise.scope.PlantScope;
import com.springmfg.ims.procurement.domain.PurchaseOrder;
import com.springmfg.ims.procurement.domain.PurchaseOrderItem;
import com.springmfg.ims.procurement.dto.CreateGrRequest;
import com.springmfg.ims.procurement.dto.GoodsReceiptDto;
import com.springmfg.ims.procurement.event.GoodsReceivedForInspectionEvent;
import com.springmfg.ims.procurement.repository.GoodsReceiptItemRepository;
import com.springmfg.ims.procurement.repository.GoodsReceiptRepository;
import com.springmfg.ims.procurement.repository.PurchaseOrderItemRepository;
import com.springmfg.ims.procurement.repository.PurchaseOrderRepository;

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
    private final SystemSettingService settingService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    private final com.springmfg.ims.enterprise.scope.PlantScope plantScope;


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

            com.springmfg.ims.enterprise.scope.PlantScope plantScope) {

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

        GoodsReceipt gr = new GoodsReceipt();
        gr.setGrNumber(numberService.next("GR"));
        gr.setPurchaseOrderId(req.purchaseOrderId());
        gr.setReceiptDate(req.receiptDate() != null ? req.receiptDate() : LocalDate.now());
        gr.setSupplierChallanNo(req.supplierChallanNo());
        gr.setVehicleNo(req.vehicleNo());
        gr.setWarehouseId(req.warehouseId());
        gr.setPlantId(po.getPlantId());
        gr.setStatus("POSTED");
        gr.setReceivedBy(currentUserId());
        gr = grRepo.save(gr);

        List<GoodsReceiptItem> savedItems = new ArrayList<>();

        for (CreateGrRequest.GrLineRequest line : req.items()) {
            PurchaseOrderItem poItem = poItemRepo.lockById(line.poItemId())
                    .orElseThrow(() -> new NotFoundException("PO item not found: " + line.poItemId()));

            // Over-receipt check (with tolerance)
            BigDecimal maxAllowed = poItem.getOrderedQty()
                    .multiply(BigDecimal.ONE.add(tolerancePct.divide(BigDecimal.valueOf(100))))
                    .subtract(poItem.getReceivedQty());
            if (line.receivedQty().compareTo(maxAllowed) > 0) {
                throw new BusinessRuleException(ErrorCode.OVER_RECEIPT,
                        String.format("Line %d: received %.3f exceeds remaining allowed %.3f",
                                poItem.getLineNo(), line.receivedQty(), maxAllowed));
            }

            boolean inspectionRequired = line.inspectionRequired() == null || line.inspectionRequired();

            // Create and persist GR item first (get its ID)
            GoodsReceiptItem grItem = new GoodsReceiptItem();
            grItem.setGoodsReceipt(gr);
            grItem.setPoItemId(line.poItemId());
            grItem.setMaterialId(poItem.getMaterialId());
            grItem.setReceivedQty(line.receivedQty());
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
                    null, line.receivedQty(),
                    line.heatNumber(), line.certificateNumber(),
                    poItem.getRate(), "QUARANTINE");

            grItem.setMaterialBatchId(batch.getId());
            grItem = grItemRepo.save(grItem);

            // Post to inventory as QUARANTINE
            postingService.post(new PostingCommand(
                    "PURCHASE_RECEIPT", "MATERIAL",
                    poItem.getMaterialId(), null,
                    batch.getId(), null,
                    req.warehouseId(), line.locationId(),
                    "QUARANTINE",
                    line.receivedQty(), null,
                    poItem.getRate(),
                    "GOODS_RECEIPT", gr.getId(), gr.getGrNumber(),
                    "GR: " + gr.getGrNumber()));

            // Update po_item received qty
            poItem.setReceivedQty(poItem.getReceivedQty().add(line.receivedQty()));
            poItemRepo.save(poItem);

            // Publish event — InspectionService listener creates the inspection
            eventPublisher.publishEvent(new GoodsReceivedForInspectionEvent(
                    grItem.getId(), batch.getId(), poItem.getMaterialId(),
                    req.warehouseId(), line.locationId(), inspectionRequired));

            savedItems.add(grItem);
        }

        poService.refreshPoStatus(po.getId());

        auditService.record("GR_POSTED", "GOODS_RECEIPT", String.valueOf(gr.getId()),
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

    // ── private helpers ───────────────────────────────────────────────────────

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.springmfg.ims.auth.service.UserPermissionCache.CachedUser u) {
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
        return new GoodsReceiptDto(
                gr.getId(), gr.getGrNumber(), gr.getPurchaseOrderId(),
                poRepo.findById(gr.getPurchaseOrderId()).map(PurchaseOrder::getPoNumber).orElse(null),
                gr.getReceiptDate(), gr.getSupplierChallanNo(), gr.getVehicleNo(),
                gr.getWarehouseId(), gr.getStatus(), gr.getReceivedBy(), itemDtos);
    }
}
