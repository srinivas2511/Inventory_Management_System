package com.springmfg.ims.wms.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.inventory.service.StockLocationQueryService;
import com.springmfg.ims.procurement.domain.GoodsReceipt;
import com.springmfg.ims.procurement.domain.GoodsReceiptItem;
import com.springmfg.ims.procurement.repository.GoodsReceiptItemRepository;
import com.springmfg.ims.procurement.repository.GoodsReceiptRepository;
import com.springmfg.ims.production.repository.ProductionOrderRepository;
import com.springmfg.ims.sales.repository.SalesOrderRepository;
import com.springmfg.ims.wms.domain.BinItemAssignment;
import com.springmfg.ims.wms.domain.TransferOrder;
import com.springmfg.ims.wms.domain.TransferOrderLine;
import com.springmfg.ims.wms.domain.WarehouseBin;
import com.springmfg.ims.wms.domain.WarehouseZone;
import com.springmfg.ims.wms.domain.WmsEnums.BinType;
import com.springmfg.ims.wms.domain.WmsEnums.MovementType;
import com.springmfg.ims.wms.domain.WmsEnums.PutAwayStrategy;
import com.springmfg.ims.wms.domain.WmsEnums.TransferOrderStatus;
import com.springmfg.ims.wms.domain.WmsEnums.ZoneType;
import com.springmfg.ims.wms.dto.WmsDtos.CrossDockRequest;
import com.springmfg.ims.wms.dto.WmsDtos.TransferOrderDto;
import com.springmfg.ims.wms.repository.BinItemAssignmentRepository;
import com.springmfg.ims.wms.repository.PutAwayStrategyRuleRepository;
import com.springmfg.ims.wms.repository.TransferOrderRepository;
import com.springmfg.ims.wms.repository.WarehouseBinRepository;
import com.springmfg.ims.wms.repository.WarehouseZoneRepository;

/**
 * Put-away planning for goods receipts (WMS-003, WMS-004) and cross-docking (WMS-008). A put-away order proposes a
 * destination bin per receipt line by the warehouse's strategy; the operator confirms it, which moves the stock.
 */
@Service
public class PutAwayService {

    public static final String DEFAULT_STRATEGY_KEY = "wms.putaway.default_strategy";

    private static final Set<ZoneType> STORAGE_ZONES = Set.of(ZoneType.BULK, ZoneType.PICKING);

    private final GoodsReceiptRepository receipts;
    private final GoodsReceiptItemRepository receiptItems;
    private final WarehouseBinRepository bins;
    private final WarehouseZoneRepository zones;
    private final BinItemAssignmentRepository assignments;
    private final PutAwayStrategyRuleRepository rules;
    private final TransferOrderRepository orders;
    private final ProductionOrderRepository productionOrders;
    private final SalesOrderRepository salesOrders;
    private final StockLocationQueryService stock;
    private final BinCapacityService capacity;
    private final TransferOrderService transferOrders;
    private final TransferOrderMapper mapper;
    private final SystemSettingService settings;
    private final AuditService audit;

    public PutAwayService(GoodsReceiptRepository receipts, GoodsReceiptItemRepository receiptItems,
                          WarehouseBinRepository bins, WarehouseZoneRepository zones,
                          BinItemAssignmentRepository assignments, PutAwayStrategyRuleRepository rules,
                          TransferOrderRepository orders, ProductionOrderRepository productionOrders,
                          SalesOrderRepository salesOrders, StockLocationQueryService stock,
                          BinCapacityService capacity, TransferOrderService transferOrders,
                          TransferOrderMapper mapper, SystemSettingService settings, AuditService audit) {
        this.receipts = receipts;
        this.receiptItems = receiptItems;
        this.bins = bins;
        this.zones = zones;
        this.assignments = assignments;
        this.rules = rules;
        this.orders = orders;
        this.productionOrders = productionOrders;
        this.salesOrders = salesOrders;
        this.stock = stock;
        this.capacity = capacity;
        this.transferOrders = transferOrders;
        this.mapper = mapper;
        this.settings = settings;
        this.audit = audit;
    }

    /**
     * Creates the put-away order of a goods receipt, or returns the one that already exists. Returns null when no
     * line needs moving (every line already sits in a bin that was chosen for it).
     */
    @Transactional
    public TransferOrderDto createPutAwayTasks(Long goodsReceiptId) {
        GoodsReceipt gr = receipts.findById(goodsReceiptId)
                .orElseThrow(() -> new NotFoundException("GoodsReceipt", goodsReceiptId));
        Optional<TransferOrder> existing = activeOrder(goodsReceiptId, MovementType.PUTAWAY);
        if (existing.isPresent()) {
            return mapper.toDto(existing.get(), true);
        }
        List<GoodsReceiptItem> items = receiptItems.findByGoodsReceiptId(goodsReceiptId);
        Planner planner = new Planner(gr.getWarehouseId());

        List<Object[]> planned = new ArrayList<>();
        for (GoodsReceiptItem item : items) {
            Long materialId = item.getMaterialId();
            String materialType = stock.itemInfo("MATERIAL", materialId)
                    .map(StockLocationQueryService.ItemInfo::materialType).orElse(null);
            PutAwayStrategy strategy = strategyFor(gr.getWarehouseId(), materialType);
            WarehouseBin target = planner.choose(strategy, materialId, item.getReceivedQty());
            if (target != null && target.getLocationId().equals(item.getLocationId())) {
                continue; // received straight into its bin
            }
            planned.add(new Object[] { item, target });
        }
        if (planned.isEmpty()) {
            return null;
        }
        TransferOrder to = transferOrders.newOrder(gr.getWarehouseId(), MovementType.PUTAWAY, "GOODS_RECEIPT",
                goodsReceiptId, "Put-away for " + gr.getGrNumber());
        int lineNo = 1;
        for (Object[] p : planned) {
            GoodsReceiptItem item = (GoodsReceiptItem) p[0];
            WarehouseBin target = (WarehouseBin) p[1];
            List<String> warnings = target == null ? List.of()
                    : capacity.check(target, "MATERIAL", item.getMaterialId(), item.getReceivedQty());
            Long fromBin = bins.findByLocationId(item.getLocationId()).map(WarehouseBin::getId).orElse(null);
            transferOrders.newLine(to.getId(), lineNo++, "MATERIAL", item.getMaterialId(), null,
                    item.getMaterialBatchId(), null, fromBin, target, item.getLocationId(), item.getReceivedQty(),
                    warnings);
        }
        audit.record("PUTAWAY_CREATE", "TRANSFER_ORDER", String.valueOf(to.getId()), null, to.getToNumber(), null);
        return mapper.toDto(to, true);
    }

    /** Manual trigger from the put-away screen: creates the order, or says why there is nothing to move. */
    @Transactional
    public TransferOrderDto putAwayForReceipt(Long goodsReceiptId) {
        TransferOrderDto dto = createPutAwayTasks(goodsReceiptId);
        if (dto == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "No line of this receipt needs put-away");
        }
        return dto;
    }

    /**
     * Stages a receipt directly for a production or sales order instead of storing it (WMS-008). An open put-away
     * order of the same receipt is replaced; one that has already moved stock blocks the request.
     */
    @Transactional
    public TransferOrderDto crossDock(CrossDockRequest req) {
        GoodsReceipt gr = receipts.findById(req.goodsReceiptId())
                .orElseThrow(() -> new NotFoundException("GoodsReceipt", req.goodsReceiptId()));
        String targetType = req.targetType().trim().toUpperCase();
        boolean production = "PRODUCTION_ORDER".equals(targetType);
        if (!production && !"SALES_ORDER".equals(targetType)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Cross-dock target must be PRODUCTION_ORDER or SALES_ORDER");
        }
        boolean targetExists = production ? productionOrders.existsById(req.targetId())
                : salesOrders.existsById(req.targetId());
        if (!targetExists) {
            throw new NotFoundException(production ? "ProductionOrder" : "SalesOrder", req.targetId());
        }
        if (activeOrder(req.goodsReceiptId(), MovementType.CROSS_DOCK).isPresent()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    gr.getGrNumber() + " already has an open cross-dock order");
        }
        activeOrder(req.goodsReceiptId(), MovementType.PUTAWAY)
                .ifPresent(po -> transferOrders.cancelInternal(po, "Replaced by cross-dock"));

        WarehouseBin staging = stagingBin(gr.getWarehouseId(), production);
        TransferOrder to = transferOrders.newOrder(gr.getWarehouseId(), MovementType.CROSS_DOCK, "GOODS_RECEIPT",
                gr.getId(), req.notes());
        to.setCrossDockTargetType(targetType);
        to.setCrossDockTargetId(req.targetId());
        orders.save(to);
        int lineNo = 1;
        for (GoodsReceiptItem item : receiptItems.findByGoodsReceiptId(gr.getId())) {
            List<String> warnings = capacity.check(staging, "MATERIAL", item.getMaterialId(), item.getReceivedQty());
            Long fromBin = bins.findByLocationId(item.getLocationId()).map(WarehouseBin::getId).orElse(null);
            transferOrders.newLine(to.getId(), lineNo++, "MATERIAL", item.getMaterialId(), null,
                    item.getMaterialBatchId(), null, fromBin, staging, item.getLocationId(), item.getReceivedQty(),
                    warnings);
        }
        audit.record("CROSS_DOCK_CREATE", "TRANSFER_ORDER", String.valueOf(to.getId()), null,
                gr.getGrNumber() + " -> " + targetType + " " + req.targetId(), null);
        return mapper.toDto(to, true);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private Optional<TransferOrder> activeOrder(Long goodsReceiptId, MovementType type) {
        return orders.findBySourceDocumentTypeAndSourceDocumentId("GOODS_RECEIPT", goodsReceiptId).stream()
                .filter(o -> o.getMovementType() == type && o.getStatus() != TransferOrderStatus.CANCELLED)
                .findFirst();
    }

    private PutAwayStrategy strategyFor(Long warehouseId, String materialType) {
        if (materialType != null) {
            var byType = rules.findByWarehouseIdAndMaterialType(warehouseId, materialType);
            if (byType.isPresent()) {
                return byType.get().getStrategy();
            }
        }
        var any = rules.findByWarehouseIdAndMaterialTypeIsNull(warehouseId);
        if (any.isPresent()) {
            return any.get().getStrategy();
        }
        try {
            return PutAwayStrategy.valueOf(settings.get(DEFAULT_STRATEGY_KEY, "FIXED_BIN").trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PutAwayStrategy.FIXED_BIN;
        }
    }

    private WarehouseBin stagingBin(Long warehouseId, boolean production) {
        List<WarehouseBin> all = bins.findActiveByWarehouse(warehouseId);
        Map<Long, WarehouseZone> zoneById = zones.findByWarehouseIdOrderByZoneCode(warehouseId).stream()
                .collect(Collectors.toMap(WarehouseZone::getId, Function.identity()));
        List<ZoneType> preference = production ? List.of(ZoneType.STAGING)
                : List.of(ZoneType.STAGING, ZoneType.DISPATCH);
        for (ZoneType type : preference) {
            Optional<WarehouseBin> hit = all.stream()
                    .filter(b -> zoneById.get(b.getZoneId()) != null && zoneById.get(b.getZoneId()).getZoneType() == type)
                    .findFirst();
            if (hit.isPresent()) {
                return hit.get();
            }
        }
        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "No staging bin is configured in this warehouse: "
                + "add a bin in a " + (production ? "STAGING" : "STAGING or DISPATCH") + " zone");
    }

    /** Chooses destination bins for one run, never giving the same empty bin to two lines. */
    private final class Planner {
        private final List<WarehouseBin> candidates;
        private final Map<Long, ZoneType> zoneTypes;
        private final Set<Long> occupied;
        private final Set<Long> usedThisRun = new HashSet<>();

        Planner(Long warehouseId) {
            List<WarehouseBin> active = bins.findActiveByWarehouse(warehouseId);
            this.zoneTypes = zones.findByWarehouseIdOrderByZoneCode(warehouseId).stream()
                    .collect(Collectors.toMap(WarehouseZone::getId, WarehouseZone::getZoneType));
            this.candidates = active.stream()
                    .filter(b -> b.getBinType() != BinType.QUARANTINE && b.getBinType() != BinType.QC)
                    .filter(b -> STORAGE_ZONES.contains(zoneTypes.get(b.getZoneId())))
                    .toList();
            this.occupied = stock.occupiedLocations(candidates.stream().map(WarehouseBin::getLocationId).toList());
        }

        WarehouseBin choose(PutAwayStrategy strategy, Long materialId, BigDecimal qty) {
            WarehouseBin chosen = switch (strategy) {
                case FIXED_BIN -> fixedBin(materialId).or(() -> nearestEmpty(materialId, qty)).orElse(null);
                case NEAREST_EMPTY -> nearestEmpty(materialId, qty).orElse(null);
                case BULK_FIRST -> bulkFirst(materialId, qty).or(() -> nearestEmpty(materialId, qty)).orElse(null);
            };
            if (chosen != null) {
                usedThisRun.add(chosen.getId());
            }
            return chosen;
        }

        private Optional<WarehouseBin> fixedBin(Long materialId) {
            List<BinItemAssignment> mine = assignments.findByMaterialId(materialId);
            return mine.stream().sorted(Comparator.comparing((BinItemAssignment a) -> !a.isPrimary()))
                    .map(a -> candidates.stream().filter(b -> b.getId().equals(a.getBinId())).findFirst())
                    .flatMap(Optional::stream).findFirst();
        }

        private Optional<WarehouseBin> nearestEmpty(Long materialId, BigDecimal qty) {
            return candidates.stream()
                    .filter(b -> !occupied.contains(b.getLocationId()) && !usedThisRun.contains(b.getId()))
                    .filter(b -> capacity.check(b, "MATERIAL", materialId, qty).isEmpty())
                    .findFirst();
        }

        private Optional<WarehouseBin> bulkFirst(Long materialId, BigDecimal qty) {
            return candidates.stream().filter(b -> zoneTypes.get(b.getZoneId()) == ZoneType.BULK)
                    .filter(b -> capacity.check(b, "MATERIAL", materialId, qty).isEmpty())
                    .findFirst();
        }
    }
}
