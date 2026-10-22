package com.mfg.ims.production.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.production.domain.MaterialIssue;
import com.mfg.ims.production.domain.MaterialIssueLine;
import com.mfg.ims.production.domain.ProductionOperation;
import com.mfg.ims.production.domain.ProductionOrder;
import com.mfg.ims.production.domain.ProductionOrderMaterial;
import com.mfg.ims.production.dto.ReturnMaterialRequest;
import com.mfg.ims.production.repository.MaterialIssueLineRepository;
import com.mfg.ims.production.repository.ProductionOperationRepository;
import com.mfg.ims.production.repository.ProductionOrderMaterialRepository;
import com.mfg.ims.production.repository.ProductionOrderRepository;
import com.mfg.ims.warehouse.domain.WarehouseLocation;
import com.mfg.ims.warehouse.repository.WarehouseLocationRepository;

/**
 * What happens to the material an order was issued (DESIGN 4.7): it is consumed by the pieces that were made, returned
 * to stock, or written off as scrap. Every issued line ends up in exactly one of the three, tracked per line, so the
 * quantities always come out of the batches this order issued.
 *
 * <p>Accounting. The GL is debited to WIP when material is issued (Dr 1501 / Cr 1401). Consumption only turns WIP
 * stock into output, so it has no journal of its own; the output receipt and the scrap journal relieve WIP. A return
 * reverses the issue (Dr 1401 / Cr 1501, bridge assignment MATERIAL_RETURN) and a write-off is scrap
 * (Dr 5202 / Cr 1501, bridge assignment PRODUCTION_REJECTION). In the ledger every leg that leaves WIP is a
 * {@code MATERIAL_CONSUMPTION} out-leg.
 */
@Service
public class OrderMaterialService {

    private static final String WIP = "WIP";

    private final ProductionOrderRepository orderRepo;
    private final ProductionOrderMaterialRepository pomRepo;
    private final ProductionOperationRepository operationRepo;
    private final MaterialIssueLineRepository lineRepo;
    private final WarehouseLocationRepository locationRepo;
    private final MaterialRepository materialRepo;
    private final InventoryPostingService postingService;
    private final AuditService auditService;

    private final com.mfg.ims.enterprise.scope.PlantScope plantScope;


    public OrderMaterialService(ProductionOrderRepository orderRepo,
                                ProductionOrderMaterialRepository pomRepo,
                                ProductionOperationRepository operationRepo,
                                MaterialIssueLineRepository lineRepo,
                                WarehouseLocationRepository locationRepo,
                                MaterialRepository materialRepo,
                                InventoryPostingService postingService,
                                AuditService auditService,

            com.mfg.ims.enterprise.scope.PlantScope plantScope) {

        this.plantScope = plantScope;
        this.orderRepo = orderRepo;
        this.pomRepo = pomRepo;
        this.operationRepo = operationRepo;
        this.lineRepo = lineRepo;
        this.locationRepo = locationRepo;
        this.materialRepo = materialRepo;
        this.postingService = postingService;
        this.auditService = auditService;
    }

    /** An issued material that is neither consumed, returned nor written off. */
    public record OpenMaterial(Long materialId, String materialCode, BigDecimal quantity, String uom) {
    }

    /**
     * Consumes material for the pieces the first operation has processed, so far (good, rejected and scrap pieces all
     * used material): the BOM quantity per piece times those pieces, never more than is still issued. It brings the
     * order up to that target, so it can run after every output (backflush) and again at completion without
     * consuming anything twice.
     */
    @Transactional
    public void consumeForOutput(Long orderId) {
        ProductionOrder order = findOrder(orderId);
        List<ProductionOperation> operations = operationRepo.findByProductionOrderIdOrderBySequenceNo(orderId);
        if (operations.isEmpty() || order.getPlannedQty().signum() <= 0) {
            return;
        }
        ProductionOperation first = operations.get(0);
        BigDecimal pieces = first.getGoodQty().add(first.getRejectedQty()).add(first.getScrapQty());
        if (pieces.signum() <= 0) {
            return;
        }
        for (ProductionOrderMaterial pom : pomRepo.lockByProductionOrderId(orderId)) {
            BigDecimal target = consumptionTarget(pom, order.getPlannedQty(), pieces);
            BigDecimal toConsume = target.subtract(pom.getConsumedQty()).min(open(pom));
            if (toConsume.signum() > 0) {
                moveOutOfWip(order, pom, toConsume, Leg.CONSUME, null, null);
            }
        }
    }

    /** BOM quantity per piece (the planned quantity without the scrap allowance) times the pieces processed. */
    static BigDecimal consumptionTarget(ProductionOrderMaterial pom, BigDecimal orderPlannedQty, BigDecimal pieces) {
        BigDecimal allowance = pom.getScrapPctApplied() != null ? pom.getScrapPctApplied() : BigDecimal.ZERO;
        BigDecimal factor = BigDecimal.ONE.add(allowance.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
        BigDecimal perPiece = pom.getPlannedQty().divide(factor, 8, RoundingMode.HALF_UP)
                .divide(orderPlannedQty, 8, RoundingMode.HALF_UP);
        return perPiece.multiply(pieces).setScale(3, RoundingMode.HALF_UP);
    }

    /** Returns unused issued material to the raw-material location it came from (Dr 1401 / Cr 1501). */
    @Transactional
    public void returnMaterial(Long orderId, ReturnMaterialRequest req) {
        ProductionOrder order = findOrder(orderId);
        requireReconcilable(order);
        ProductionOrderMaterial pom = pomRepo.lockByProductionOrderId(orderId).stream()
                .filter(m -> m.getMaterialId().equals(req.materialId())).findFirst()
                .orElseThrow(() -> new NotFoundException("Material " + req.materialId() + " is not on order " + orderId));
        BigDecimal open = open(pom);
        if (req.quantity().compareTo(open) > 0) {
            throw new BusinessRuleException(ErrorCode.OVER_CONSUMPTION,
                    "Cannot return " + req.quantity() + ": only " + open + " of the issued material is neither consumed, "
                            + "returned nor written off");
        }
        moveOutOfWip(order, pom, req.quantity(), Leg.RETURN, null, req.remarks());
        auditService.record("PROD_ORDER_MATERIAL_RETURN", "ProductionOrder", String.valueOf(orderId),
                null, req.materialId() + ":" + req.quantity().toPlainString(), req.remarks());
    }

    /** Writes the remaining issued material off as scrap (Dr 5202 / Cr 1501); a reason is mandatory and audited. */
    @Transactional
    public void writeOffRemaining(Long orderId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A reason is required to write material off");
        }
        ProductionOrder order = findOrder(orderId);
        requireReconcilable(order);
        List<String> written = new ArrayList<>();
        for (ProductionOrderMaterial pom : pomRepo.lockByProductionOrderId(orderId)) {
            BigDecimal open = open(pom);
            if (open.signum() > 0) {
                moveOutOfWip(order, pom, open, Leg.WRITE_OFF, reason, null);
                written.add(pom.getMaterialId() + ":" + open.toPlainString());
            }
        }
        auditService.record("PROD_ORDER_MATERIAL_WRITE_OFF", "ProductionOrder", String.valueOf(orderId),
                null, String.join(",", written), reason);
    }

    /** The issued material that still has to be returned or written off before the order can close. */
    @Transactional(readOnly = true)
    public List<OpenMaterial> openMaterials(Long orderId) {
        List<OpenMaterial> open = new ArrayList<>();
        for (ProductionOrderMaterial pom : pomRepo.findByProductionOrderId(orderId)) {
            BigDecimal qty = open(pom);
            if (qty.signum() != 0) {
                String code = materialRepo.findById(pom.getMaterialId()).map(Material::getMaterialCode)
                        .orElse(String.valueOf(pom.getMaterialId()));
                open.add(new OpenMaterial(pom.getMaterialId(), code, qty, pom.getUom()));
            }
        }
        return open;
    }

    // ---- internals ------------------------------------------------------------------------------------------------

    private enum Leg { CONSUME, RETURN, WRITE_OFF }

    private static BigDecimal open(ProductionOrderMaterial pom) {
        return pom.getIssuedQty().subtract(pom.getConsumedQty()).subtract(pom.getReturnedQty()).subtract(pom.getScrapQty());
    }

    /** Takes {@code qty} out of this order's issued lines, oldest line first, and posts the legs for each. */
    private void moveOutOfWip(ProductionOrder order, ProductionOrderMaterial pom, BigDecimal qty, Leg leg,
                              String reason, String remarks) {
        BigDecimal left = qty;
        for (MaterialIssueLine line : lineRepo.findByProductionOrderAndMaterial(order.getId(), pom.getMaterialId())) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal take = line.openQty().min(left);
            if (take.signum() <= 0) {
                continue;
            }
            MaterialIssue issue = line.getMaterialIssue();
            Long warehouseId = issue.getFromWarehouseId();
            String note = remarks != null ? remarks : reason;

            postingService.post(new PostingCommand(
                    "MATERIAL_CONSUMPTION", "MATERIAL",
                    pom.getMaterialId(), null, line.getMaterialBatchId(), null,
                    warehouseId, line.getToLocationId(), WIP,
                    take, null, null,
                    "PRODUCTION_ORDER", order.getId(), order.getOrderNumber(), leg.name()));

            switch (leg) {
                case CONSUME -> line.setConsumedQty(line.getConsumedQty().add(take));
                case RETURN -> {
                    WarehouseLocation home = locationRepo.findById(line.getFromLocationId())
                            .orElseThrow(() -> new NotFoundException("Location not found: " + line.getFromLocationId()));
                    postingService.post(new PostingCommand(
                            "MATERIAL_RETURN", "MATERIAL",
                            pom.getMaterialId(), null, line.getMaterialBatchId(), null,
                            home.getWarehouse().getId(), home.getId(), "AVAILABLE",
                            take, null, line.getUnitCost(),
                            "PRODUCTION_ORDER", order.getId(), order.getOrderNumber(), note));
                    line.setReturnedQty(line.getReturnedQty().add(take));
                }
                case WRITE_OFF -> {
                    WarehouseLocation scrap = scrapLocation(warehouseId);
                    postingService.post(new PostingCommand(
                            "PRODUCTION_REJECTION", "MATERIAL",
                            pom.getMaterialId(), null, line.getMaterialBatchId(), null,
                            warehouseId, scrap.getId(), "SCRAP",
                            take, null, line.getUnitCost(),
                            "PRODUCTION_ORDER", order.getId(), order.getOrderNumber(), note));
                    line.setWrittenOffQty(line.getWrittenOffQty().add(take));
                }
            }
            lineRepo.save(line);
            left = left.subtract(take);
        }
        if (left.signum() > 0) {
            throw new BusinessRuleException(ErrorCode.OVER_CONSUMPTION,
                    "Order " + order.getOrderNumber() + " has no issued stock left for " + left.toPlainString()
                            + " of material " + pom.getMaterialId());
        }
        switch (leg) {
            case CONSUME -> pom.setConsumedQty(pom.getConsumedQty().add(qty));
            case RETURN -> pom.setReturnedQty(pom.getReturnedQty().add(qty));
            case WRITE_OFF -> pom.setScrapQty(pom.getScrapQty().add(qty));
        }
        pomRepo.save(pom);
    }

    private WarehouseLocation scrapLocation(Long warehouseId) {
        return locationRepo.findByWarehouseIdAndActiveTrue(warehouseId).stream()
                .filter(l -> "SCRAP".equals(l.getLocationType())).findFirst()
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "No active SCRAP location in warehouse " + warehouseId + " to write the material off into"));
    }

    private void requireReconcilable(ProductionOrder order) {
        if (!List.of("RELEASED", "IN_PROGRESS", "COMPLETED").contains(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Material of an order in status " + order.getStatus() + " can no longer be returned or written off");
        }
    }

    private ProductionOrder findOrder(Long orderId) {
        ProductionOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        plantScope.assertVisible(order.getPlantId(), "Production order", orderId);
        return order;
    }
}
