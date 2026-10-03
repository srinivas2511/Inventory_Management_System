package com.springmfg.ims.wms.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.inventory.service.StockLocationQueryService;
import com.springmfg.ims.inventory.service.StockLocationQueryService.LocationStock;
import com.springmfg.ims.sales.domain.SalesOrder;
import com.springmfg.ims.sales.domain.SalesOrderItem;
import com.springmfg.ims.sales.domain.SalesReservation;
import com.springmfg.ims.sales.repository.SalesOrderItemRepository;
import com.springmfg.ims.sales.repository.SalesOrderRepository;
import com.springmfg.ims.sales.repository.SalesReservationRepository;
import com.springmfg.ims.wms.domain.PickWave;
import com.springmfg.ims.wms.domain.PickWaveOrder;
import com.springmfg.ims.wms.domain.TransferOrder;
import com.springmfg.ims.wms.domain.TransferOrderLine;
import com.springmfg.ims.wms.domain.WarehouseBin;
import com.springmfg.ims.wms.domain.WarehouseZone;
import com.springmfg.ims.wms.domain.WmsEnums.MovementType;
import com.springmfg.ims.wms.domain.WmsEnums.TransferLineStatus;
import com.springmfg.ims.wms.domain.WmsEnums.TransferOrderStatus;
import com.springmfg.ims.wms.domain.WmsEnums.WaveStatus;
import com.springmfg.ims.wms.dto.WmsDtos.CreateWaveRequest;
import com.springmfg.ims.wms.dto.WmsDtos.PickListLineDto;
import com.springmfg.ims.wms.dto.WmsDtos.PickOrderQty;
import com.springmfg.ims.wms.dto.WmsDtos.TransferOrderDto;
import com.springmfg.ims.wms.dto.WmsDtos.WaveDto;
import com.springmfg.ims.wms.repository.PickWaveOrderRepository;
import com.springmfg.ims.wms.repository.PickWaveRepository;
import com.springmfg.ims.wms.repository.TransferOrderLineRepository;
import com.springmfg.ims.wms.repository.TransferOrderRepository;
import com.springmfg.ims.wms.repository.WarehouseBinRepository;
import com.springmfg.ims.wms.repository.WarehouseZoneRepository;

/**
 * Picking transfer orders for sales orders, with first-in-first-out bin picking (WMS-005), and multi-order waves
 * with a consolidated, zone-ordered pick list. A pick line without a destination bin is a verified pick: the
 * dispatch itself posts the stock out, so picking posts nothing.
 */
@Service
public class PickingService {

    private static final Set<String> PICKABLE_ORDER_STATUSES = Set.of("APPROVED", "ALLOCATED", "PARTIALLY_DISPATCHED");

    private final SalesOrderRepository salesOrders;
    private final SalesOrderItemRepository salesItems;
    private final SalesReservationRepository reservations;
    private final PickWaveRepository waves;
    private final PickWaveOrderRepository waveOrders;
    private final TransferOrderRepository orders;
    private final TransferOrderLineRepository lines;
    private final WarehouseBinRepository bins;
    private final WarehouseZoneRepository zones;
    private final StockLocationQueryService stock;
    private final TransferOrderService transferOrders;
    private final TransferOrderMapper mapper;
    private final DocumentNumberService numbers;
    private final AuditService audit;

    public PickingService(SalesOrderRepository salesOrders, SalesOrderItemRepository salesItems,
                          SalesReservationRepository reservations, PickWaveRepository waves,
                          PickWaveOrderRepository waveOrders, TransferOrderRepository orders,
                          TransferOrderLineRepository lines, WarehouseBinRepository bins,
                          WarehouseZoneRepository zones, StockLocationQueryService stock,
                          TransferOrderService transferOrders, TransferOrderMapper mapper,
                          DocumentNumberService numbers, AuditService audit) {
        this.salesOrders = salesOrders;
        this.salesItems = salesItems;
        this.reservations = reservations;
        this.waves = waves;
        this.waveOrders = waveOrders;
        this.orders = orders;
        this.lines = lines;
        this.bins = bins;
        this.zones = zones;
        this.stock = stock;
        this.transferOrders = transferOrders;
        this.mapper = mapper;
        this.numbers = numbers;
        this.audit = audit;
    }

    // ---- picking orders ---------------------------------------------------------------------------------------

    /**
     * Creates the picking order(s) of a sales order: one per warehouse that holds stock for it, lines in
     * first-in-first-out batch order. Returns the existing orders when the sales order was already picked for.
     * Returns an empty list when no approved stock exists at all.
     */
    @Transactional
    public List<TransferOrderDto> createPickingTasks(Long salesOrderId, Long warehouseFilter) {
        return createPickingTasks(salesOrderId, warehouseFilter, null).stream()
                .map(t -> mapper.toDto(t, true)).toList();
    }

    List<TransferOrder> createPickingTasks(Long salesOrderId, Long warehouseFilter, Long waveId) {
        SalesOrder order = salesOrders.findById(salesOrderId)
                .orElseThrow(() -> new NotFoundException("SalesOrder", salesOrderId));
        if (!PICKABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Sales order " + order.getOrderNumber() + " is " + order.getStatus() + " and cannot be picked");
        }
        List<TransferOrder> existing = activePickingOrders(salesOrderId);
        if (!existing.isEmpty()) {
            if (waveId != null) {
                for (TransferOrder t : existing) {
                    if (t.getWaveId() != null && !t.getWaveId().equals(waveId)) {
                        throw new ConflictException(ErrorCode.DUPLICATE_KEY, order.getOrderNumber()
                                + " is already in another pick wave");
                    }
                    t.setWaveId(waveId);
                    orders.save(t);
                }
            }
            return existing;
        }

        Map<Long, List<Pick>> byWarehouse = new LinkedHashMap<>();
        List<String> shortages = new ArrayList<>();
        for (SalesOrderItem item : salesItems.findBySalesOrderId(salesOrderId)) {
            BigDecimal need = item.getOrderedQty().subtract(item.getDispatchedQty());
            if (need.signum() <= 0) {
                continue;
            }
            Map<String, BigDecimal> ownReserved = reservations
                    .findBySalesOrderItemIdAndStatus(item.getId(), "ACTIVE").stream()
                    .collect(Collectors.toMap(r -> r.getFgBatchId() + ":" + r.getLocationId(),
                            SalesReservation::getReservedQty, BigDecimal::add));
            for (LocationStock row : stock.pickableStock("PRODUCT", item.getProductId(), warehouseFilter)) {
                if (need.signum() <= 0) {
                    break;
                }
                BigDecimal own = ownReserved.getOrDefault(row.batchId() + ":" + row.locationId(), BigDecimal.ZERO);
                BigDecimal available = row.available().add(own).min(row.qtyOnHand());
                BigDecimal take = available.min(need);
                if (take.signum() > 0) {
                    byWarehouse.computeIfAbsent(row.warehouseId(), k -> new ArrayList<>()).add(new Pick(row, take));
                    need = need.subtract(take);
                }
            }
            if (need.signum() > 0) {
                String code = stock.itemInfo("PRODUCT", item.getProductId()).map(i -> i.code()).orElse("product " + item.getProductId());
                shortages.add(code + " short by " + need.stripTrailingZeros().toPlainString());
            }
        }

        List<TransferOrder> created = new ArrayList<>();
        for (Map.Entry<Long, List<Pick>> e : byWarehouse.entrySet()) {
            String notes = shortages.isEmpty() ? null : "Shortage: " + String.join("; ", shortages);
            TransferOrder to = transferOrders.newOrder(e.getKey(), MovementType.PICKING, "SALES_ORDER", salesOrderId,
                    notes == null ? "Pick for " + order.getOrderNumber() : notes);
            if (waveId != null) {
                to.setWaveId(waveId);
                orders.save(to);
            }
            Map<Long, WarehouseBin> binByLocation = bins.findByLocationIdIn(
                    e.getValue().stream().map(p -> p.row().locationId()).collect(Collectors.toSet())).stream()
                    .collect(Collectors.toMap(WarehouseBin::getLocationId, Function.identity()));
            int lineNo = 1;
            for (Pick p : e.getValue()) {
                WarehouseBin bin = binByLocation.get(p.row().locationId());
                transferOrders.newLine(to.getId(), lineNo++, "PRODUCT", null, p.row().productId(), p.row().batchId(),
                        p.row().stockStatus(), bin == null ? null : bin.getId(), null, p.row().locationId(), p.qty(), null);
            }
            audit.record("PICKING_CREATE", "TRANSFER_ORDER", String.valueOf(to.getId()), null,
                    to.getToNumber() + " for " + order.getOrderNumber(), null);
            created.add(to);
        }
        return created;
    }

    private record Pick(LocationStock row, BigDecimal qty) {
    }

    private List<TransferOrder> activePickingOrders(Long salesOrderId) {
        return orders.findBySourceDocumentTypeAndSourceDocumentId("SALES_ORDER", salesOrderId).stream()
                .filter(o -> o.getMovementType() == MovementType.PICKING && o.getStatus() != TransferOrderStatus.CANCELLED)
                .toList();
    }

    // ---- waves -------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<WaveDto> searchWaves(WaveStatus status, Long warehouseId, Pageable pageable) {
        return waves.search(status, warehouseId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public WaveDto getWave(Long id) {
        return toDto(requireWave(id));
    }

    @Transactional
    public WaveDto createWave(CreateWaveRequest req) {
        List<Long> orderIds = req.salesOrderIds().stream().distinct().toList();
        for (Long orderId : orderIds) {
            if (!salesOrders.existsById(orderId)) {
                throw new NotFoundException("SalesOrder", orderId);
            }
            for (PickWaveOrder wo : waveOrders.findBySalesOrderId(orderId)) {
                PickWave other = waves.findById(wo.getWaveId()).orElse(null);
                if (other != null && (other.getStatus() == WaveStatus.CREATED || other.getStatus() == WaveStatus.RELEASED)) {
                    throw new ConflictException(ErrorCode.DUPLICATE_KEY,
                            "Sales order " + orderId + " is already in wave " + other.getWaveNumber());
                }
            }
        }
        PickWave wave = new PickWave();
        wave.setWaveNumber(numbers.next("WV"));
        wave.setWarehouseId(req.warehouseId());
        wave.setStatus(WaveStatus.CREATED);
        wave.setNotes(WmsSupport.clip(req.notes(), 300));
        wave = waves.save(wave);

        int pickOrders = 0;
        for (Long orderId : orderIds) {
            PickWaveOrder wo = new PickWaveOrder();
            wo.setWaveId(wave.getId());
            wo.setSalesOrderId(orderId);
            waveOrders.save(wo);
            pickOrders += createPickingTasks(orderId, req.warehouseId(), wave.getId()).size();
        }
        if (pickOrders == 0) {
            throw new BusinessRuleException(ErrorCode.INSUFFICIENT_STOCK,
                    "None of the sales orders has approved stock to pick");
        }
        audit.record("WAVE_CREATE", "PICK_WAVE", String.valueOf(wave.getId()), null, wave.getWaveNumber(), null);
        return toDto(wave);
    }

    @Transactional
    public WaveDto releaseWave(Long id) {
        PickWave wave = waves.lockById(id).orElseThrow(() -> new NotFoundException("PickWave", id));
        if (!wave.getStatus().canMoveTo(WaveStatus.RELEASED)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    wave.getWaveNumber() + " is " + wave.getStatus() + " and cannot be released");
        }
        wave.setStatus(WaveStatus.RELEASED);
        wave.setReleasedAt(Instant.now());
        wave.setReleasedBy(WmsSupport.currentUserId());
        waves.save(wave);
        audit.record("WAVE_RELEASE", "PICK_WAVE", String.valueOf(id), "CREATED", "RELEASED", null);
        return toDto(wave);
    }

    @Transactional
    public WaveDto cancelWave(Long id, String reason) {
        PickWave wave = waves.lockById(id).orElseThrow(() -> new NotFoundException("PickWave", id));
        if (!wave.getStatus().canMoveTo(WaveStatus.CANCELLED)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    wave.getWaveNumber() + " is " + wave.getStatus() + " and cannot be cancelled");
        }
        WaveStatus old = wave.getStatus();
        for (TransferOrder t : orders.findByWaveId(id)) {
            if (t.getStatus().isOpen()) {
                transferOrders.cancelInternal(t, "Wave " + wave.getWaveNumber() + " cancelled");
            }
        }
        wave.setStatus(WaveStatus.CANCELLED);
        waves.save(wave);
        audit.record("WAVE_CANCEL", "PICK_WAVE", String.valueOf(id), old.name(), "CANCELLED", reason);
        return toDto(wave);
    }

    /**
     * The consolidated pick list of a wave: what is still to pick, summed per bin, item and batch across all orders
     * of the wave, ordered by zone then bin so a picker walks each zone once. {@code zoneId} limits it to one zone.
     */
    @Transactional(readOnly = true)
    public List<PickListLineDto> pickList(Long waveId, Long zoneId) {
        requireWave(waveId);
        List<TransferOrder> tos = orders.findByWaveId(waveId).stream()
                .filter(o -> o.getStatus() != TransferOrderStatus.CANCELLED).toList();
        Map<Long, TransferOrder> toById = tos.stream().collect(Collectors.toMap(TransferOrder::getId, Function.identity()));
        List<TransferOrderLine> open = lines.findByTransferOrderIdIn(toById.keySet()).stream()
                .filter(l -> l.getStatus() == TransferLineStatus.OPEN || l.getStatus() == TransferLineStatus.PARTIAL)
                .toList();

        Map<Long, WarehouseBin> binById = bins.findByIdIn(open.stream().map(TransferOrderLine::getFromBinId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(WarehouseBin::getId, Function.identity()));
        Map<Long, WarehouseZone> zoneById = zones.findByIdIn(binById.values().stream().map(WarehouseBin::getZoneId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(WarehouseZone::getId, Function.identity()));
        Map<Long, String> orderNumbers = salesOrders.findAllById(tos.stream().map(TransferOrder::getSourceDocumentId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SalesOrder::getId, SalesOrder::getOrderNumber));

        record Key(Long binId, Long locationId, String itemType, Long itemId, Long batchId) {
        }
        Map<Key, List<TransferOrderLine>> grouped = new LinkedHashMap<>();
        for (TransferOrderLine l : open) {
            WarehouseBin bin = l.getFromBinId() == null ? null : binById.get(l.getFromBinId());
            if (zoneId != null && (bin == null || !zoneId.equals(bin.getZoneId()))) {
                continue;
            }
            Long itemId = "MATERIAL".equals(l.getItemType()) ? l.getMaterialId() : l.getProductId();
            grouped.computeIfAbsent(new Key(l.getFromBinId(), l.getFromLocationId(), l.getItemType(), itemId,
                    l.getBatchId()), k -> new ArrayList<>()).add(l);
        }

        List<PickListLineDto> out = new ArrayList<>();
        for (Map.Entry<Key, List<TransferOrderLine>> e : grouped.entrySet()) {
            Key k = e.getKey();
            WarehouseBin bin = k.binId() == null ? null : binById.get(k.binId());
            WarehouseZone zone = bin == null ? null : zoneById.get(bin.getZoneId());
            var info = stock.itemInfo(k.itemType(), k.itemId());
            BigDecimal total = BigDecimal.ZERO;
            Map<Long, BigDecimal> perTo = new LinkedHashMap<>();
            for (TransferOrderLine l : e.getValue()) {
                BigDecimal remaining = l.getPlannedQty().subtract(l.getConfirmedQty());
                total = total.add(remaining);
                perTo.merge(l.getTransferOrderId(), remaining, BigDecimal::add);
            }
            List<PickOrderQty> orderQty = perTo.entrySet().stream().map(p -> {
                TransferOrder t = toById.get(p.getKey());
                return new PickOrderQty(t.getSourceDocumentId(), orderNumbers.get(t.getSourceDocumentId()), t.getId(),
                        p.getValue());
            }).toList();
            out.add(new PickListLineDto(zone == null ? null : zone.getId(), zone == null ? null : zone.getZoneCode(),
                    k.binId(), bin == null ? null : bin.getBinCode(), k.locationId(), k.itemType(), k.itemId(),
                    info.map(i -> i.code()).orElse(null), info.map(i -> i.name()).orElse(null), k.batchId(),
                    stock.batchNumber(k.itemType(), k.batchId()).orElse(null), total, orderQty));
        }
        out.sort(Comparator.comparing((PickListLineDto l) -> l.zoneCode() == null ? "~" : l.zoneCode())
                .thenComparing(l -> l.binCode() == null ? "~" : l.binCode())
                .thenComparing(l -> l.itemCode() == null ? "" : l.itemCode()));
        return out;
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private PickWave requireWave(Long id) {
        return waves.findById(id).orElseThrow(() -> new NotFoundException("PickWave", id));
    }

    private WaveDto toDto(PickWave w) {
        return new WaveDto(w.getId(), w.getWaveNumber(), w.getWarehouseId(), w.getStatus(), w.getReleasedAt(),
                w.getNotes(), waveOrders.findByWaveId(w.getId()).stream().map(PickWaveOrder::getSalesOrderId).toList(),
                orders.findByWaveId(w.getId()).stream().map(TransferOrder::getId).toList(), w.getVersion());
    }
}
