package com.mfg.ims.wms.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.common.search.SearchText;
import com.mfg.ims.iam.repository.UserRepository;
import com.mfg.ims.inventory.dto.TransferCommand;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.inventory.service.StockLocationQueryService;
import com.mfg.ims.inventory.service.StockLocationQueryService.LocationStock;
import com.mfg.ims.warehouse.repository.WarehouseRepository;
import com.mfg.ims.wms.domain.PickWave;
import com.mfg.ims.wms.domain.TransferOrder;
import com.mfg.ims.wms.domain.TransferOrderLine;
import com.mfg.ims.wms.domain.WarehouseBin;
import com.mfg.ims.wms.domain.WarehouseZone;
import com.mfg.ims.wms.domain.WmsEnums.BinType;
import com.mfg.ims.wms.domain.WmsEnums.MovementType;
import com.mfg.ims.wms.domain.WmsEnums.TransferLineStatus;
import com.mfg.ims.wms.domain.WmsEnums.TransferOrderStatus;
import com.mfg.ims.wms.domain.WmsEnums.WaveStatus;
import com.mfg.ims.wms.domain.WmsEnums.ZoneType;
import com.mfg.ims.wms.dto.WmsDtos.ConfirmLineRequest;
import com.mfg.ims.wms.dto.WmsDtos.ConfirmRequest;
import com.mfg.ims.wms.dto.WmsDtos.ConfirmResultDto;
import com.mfg.ims.wms.dto.WmsDtos.CreateTransferRequest;
import com.mfg.ims.wms.dto.WmsDtos.TransferLineRequest;
import com.mfg.ims.wms.dto.WmsDtos.TransferOrderDto;
import com.mfg.ims.wms.repository.PickWaveRepository;
import com.mfg.ims.wms.repository.TransferOrderLineRepository;
import com.mfg.ims.wms.repository.TransferOrderRepository;
import com.mfg.ims.wms.repository.WarehouseBinRepository;
import com.mfg.ims.wms.repository.WarehouseZoneRepository;

/**
 * Transfer orders: the work orders for put-away, picking, internal moves and cross-docking (WMS-006). Confirming a
 * line that has a destination bin posts a STOCK_TRANSFER through {@link InventoryPostingService}, the only writer of
 * stock; a picking line without a destination is a verified pick and posts nothing, because the dispatch itself
 * posts the stock out.
 */
@Service
public class TransferOrderService {

    private final TransferOrderRepository orders;
    private final TransferOrderLineRepository lines;
    private final WarehouseBinRepository bins;
    private final WarehouseZoneRepository zones;
    private final PickWaveRepository waves;
    private final WarehouseRepository warehouses;
    private final UserRepository users;
    private final InventoryPostingService posting;
    private final StockLocationQueryService stock;
    private final BinCapacityService capacity;
    private final BinService binService;
    private final DocumentNumberService numbers;
    private final AuditService audit;
    private final TransferOrderMapper mapper;

    public TransferOrderService(TransferOrderRepository orders, TransferOrderLineRepository lines,
                                WarehouseBinRepository bins, WarehouseZoneRepository zones, PickWaveRepository waves,
                                WarehouseRepository warehouses, UserRepository users,
                                InventoryPostingService posting, StockLocationQueryService stock,
                                BinCapacityService capacity, BinService binService, DocumentNumberService numbers,
                                AuditService audit, TransferOrderMapper mapper) {
        this.orders = orders;
        this.lines = lines;
        this.bins = bins;
        this.zones = zones;
        this.waves = waves;
        this.warehouses = warehouses;
        this.users = users;
        this.posting = posting;
        this.stock = stock;
        this.capacity = capacity;
        this.binService = binService;
        this.numbers = numbers;
        this.audit = audit;
        this.mapper = mapper;
    }

    // ---- queries -------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<TransferOrderDto> search(TransferOrderStatus status, MovementType type, Long warehouseId,
                                         Long assignedTo, Long waveId, String q, Pageable pageable) {
        return orders.search(status, type, warehouseId, assignedTo, waveId, SearchText.normalise(q), pageable)
                .map(t -> mapper.toDto(t, false));
    }

    @Transactional(readOnly = true)
    public TransferOrderDto get(Long id) {
        return mapper.toDto(require(id), true);
    }

    // ---- creation ------------------------------------------------------------------------------------------

    /** Internal move between bins or from a plain location into a bin (INTERNAL_TRANSFER). */
    @Transactional
    public TransferOrderDto createInternalTransfer(CreateTransferRequest req) {
        warehouses.findById(req.warehouseId()).orElseThrow(() -> new NotFoundException("Warehouse", req.warehouseId()));
        TransferOrder to = newOrder(req.warehouseId(), MovementType.INTERNAL_TRANSFER, null, null, req.notes());
        int lineNo = 1;
        for (TransferLineRequest l : req.lines()) {
            boolean material = "MATERIAL".equals(l.itemType());
            if (!material && !"PRODUCT".equals(l.itemType())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Item type must be MATERIAL or PRODUCT");
            }
            Long itemId = material ? l.materialId() : l.productId();
            if (itemId == null || (material ? l.productId() : l.materialId()) != null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Line " + lineNo + ": give the " + (material ? "material" : "product") + " only");
            }
            WarehouseBin toBin = requireBinInWarehouse(l.toBinId(), req.warehouseId());
            Long fromLocation;
            Long fromBinId = null;
            if (l.fromBinId() != null) {
                WarehouseBin from = requireBinInWarehouse(l.fromBinId(), req.warehouseId());
                fromLocation = from.getLocationId();
                fromBinId = from.getId();
            } else if (l.fromLocationId() != null) {
                fromLocation = l.fromLocationId();
                fromBinId = bins.findByLocationId(fromLocation).map(WarehouseBin::getId).orElse(null);
            } else {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Line " + lineNo + ": give the bin or location to move from");
            }
            if (fromLocation.equals(toBin.getLocationId())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Line " + lineNo + ": source and destination are the same");
            }
            BigDecimal onHand = stock.stockOfBatchAt(fromLocation, l.itemType(), itemId, l.batchId()).stream()
                    .map(LocationStock::available).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (onHand.compareTo(l.quantity()) < 0) {
                throw new BusinessRuleException(ErrorCode.INSUFFICIENT_STOCK,
                        "Line " + lineNo + ": only " + onHand.stripTrailingZeros().toPlainString()
                                + " available at the source");
            }
            List<String> warnings = capacity.checkAndEnforce(toBin, l.itemType(), itemId, l.quantity());
            lines.save(newLine(to.getId(), lineNo++, l.itemType(), l.materialId(), l.productId(), l.batchId(), null,
                    fromBinId, toBin, fromLocation, l.quantity(), warnings));
        }
        audit.record("TO_CREATE", "TRANSFER_ORDER", String.valueOf(to.getId()), null, to.getToNumber(), null);
        return mapper.toDto(to, true);
    }

    /** Creates the header with a fresh number; used by every flow that builds transfer orders. */
    TransferOrder newOrder(Long warehouseId, MovementType type, String sourceType, Long sourceId, String notes) {
        TransferOrder to = new TransferOrder();
        to.setToNumber(numbers.next("TO"));
        to.setWarehouseId(warehouseId);
        to.setMovementType(type);
        to.setSourceDocumentType(sourceType);
        to.setSourceDocumentId(sourceId);
        to.setStatus(TransferOrderStatus.CREATED);
        to.setNotes(WmsSupport.clip(notes, 500));
        return orders.save(to);
    }

    TransferOrderLine newLine(Long toId, int lineNo, String itemType, Long materialId, Long productId, Long batchId,
                              String stockStatus, Long fromBinId, WarehouseBin toBin, Long fromLocationId,
                              BigDecimal qty, List<String> warnings) {
        TransferOrderLine l = new TransferOrderLine();
        l.setTransferOrderId(toId);
        l.setLineNo(lineNo);
        l.setItemType(itemType);
        l.setMaterialId(materialId);
        l.setProductId(productId);
        l.setBatchId(batchId);
        l.setStockStatus(stockStatus);
        l.setFromBinId(fromBinId);
        l.setFromLocationId(fromLocationId);
        l.setPlannedQty(qty);
        if (toBin != null) {
            l.setToBinId(toBin.getId());
            l.setToLocationId(toBin.getLocationId());
        }
        l.setWarning(warnings == null || warnings.isEmpty() ? null : WmsSupport.clip(String.join("; ", warnings), 300));
        return lines.save(l);
    }

    // ---- lifecycle -----------------------------------------------------------------------------------------

    @Transactional
    public TransferOrderDto assign(Long id, Long userId) {
        TransferOrder to = require(id);
        requireMovementAuthority(to.getMovementType());
        requireOpen(to);
        if (!users.existsById(userId)) {
            throw new NotFoundException("User", userId);
        }
        Long old = to.getAssignedTo();
        to.setAssignedTo(userId);
        orders.save(to);
        audit.record("TO_ASSIGN", "TRANSFER_ORDER", String.valueOf(id), String.valueOf(old), String.valueOf(userId), null);
        return mapper.toDto(to, true);
    }

    @Transactional
    public TransferOrderDto start(Long id) {
        TransferOrder to = require(id);
        requireMovementAuthority(to.getMovementType());
        if (!to.getStatus().canMoveTo(TransferOrderStatus.IN_PROGRESS) || to.getStatus() != TransferOrderStatus.CREATED) {
            throw illegal(to, TransferOrderStatus.IN_PROGRESS);
        }
        to.setStatus(TransferOrderStatus.IN_PROGRESS);
        orders.save(to);
        audit.record("TO_START", "TRANSFER_ORDER", String.valueOf(id), "CREATED", "IN_PROGRESS", null);
        return mapper.toDto(to, true);
    }

    @Transactional
    public TransferOrderDto cancel(Long id, String reason) {
        TransferOrder to = require(id);
        requireMovementAuthority(to.getMovementType());
        cancelInternal(to, reason);
        return mapper.toDto(to, true);
    }

    /** Cancels an order that has confirmed nothing; shared by wave and cross-dock flows. */
    @Transactional
    public void cancelInternal(TransferOrder to, String reason) {
        if (!to.getStatus().canMoveTo(TransferOrderStatus.CANCELLED)) {
            throw illegal(to, TransferOrderStatus.CANCELLED);
        }
        List<TransferOrderLine> ls = lines.findByTransferOrderIdOrderByLineNo(to.getId());
        if (ls.stream().anyMatch(l -> l.getConfirmedQty().signum() > 0)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    to.getToNumber() + " already has confirmed quantities and cannot be cancelled");
        }
        TransferOrderStatus old = to.getStatus();
        for (TransferOrderLine l : ls) {
            l.setStatus(TransferLineStatus.CANCELLED);
            lines.save(l);
        }
        to.setStatus(TransferOrderStatus.CANCELLED);
        to.setCompletedAt(Instant.now());
        orders.save(to);
        audit.record("TO_CANCEL", "TRANSFER_ORDER", String.valueOf(to.getId()), old.name(), "CANCELLED", reason);
        refreshWave(to.getWaveId());
    }

    @Transactional
    public TransferOrderDto setTarget(Long id, Long lineId, Long toBinId) {
        TransferOrder to = require(id);
        requireMovementAuthority(to.getMovementType());
        requireOpen(to);
        TransferOrderLine line = lines.findById(lineId)
                .filter(l -> l.getTransferOrderId().equals(id))
                .orElseThrow(() -> new NotFoundException("TransferOrderLine", lineId));
        if (line.getStatus() != TransferLineStatus.OPEN) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Line " + line.getLineNo() + " is already " + line.getStatus());
        }
        WarehouseBin bin = requireBinInWarehouse(toBinId, to.getWarehouseId());
        Long itemId = itemId(line);
        List<String> warnings = capacity.checkAndEnforce(bin, line.getItemType(), itemId, line.getPlannedQty());
        line.setToBinId(bin.getId());
        line.setToLocationId(bin.getLocationId());
        line.setWarning(warnings.isEmpty() ? null : WmsSupport.clip(String.join("; ", warnings), 300));
        lines.save(line);
        audit.record("TO_SET_TARGET", "TRANSFER_ORDER", String.valueOf(id), null, bin.getBinCode(), null);
        return mapper.toDto(to, true);
    }

    // ---- confirmation --------------------------------------------------------------------------------------

    @Transactional
    public ConfirmResultDto confirm(Long id, ConfirmRequest req) {
        TransferOrder to = orders.lockById(id).orElseThrow(() -> new NotFoundException("TransferOrder", id));
        requireMovementAuthority(to.getMovementType());
        requireOpen(to);

        Map<Long, TransferOrderLine> byId = lines.findByTransferOrderIdOrderByLineNo(id).stream()
                .collect(Collectors.toMap(TransferOrderLine::getId, Function.identity()));
        List<ConfirmLineRequest> ordered = new ArrayList<>(req.lines());
        ordered.sort(Comparator.comparing(c -> {
            TransferOrderLine l = byId.get(c.lineId());
            return l == null ? Integer.MAX_VALUE : l.getLineNo();
        }));

        List<String> warnings = new ArrayList<>();
        Set<Long> dirtyBins = new HashSet<>();
        Set<Long> seen = new HashSet<>();
        for (ConfirmLineRequest c : ordered) {
            TransferOrderLine line = byId.get(c.lineId());
            if (line == null) {
                throw new NotFoundException("TransferOrderLine", c.lineId());
            }
            if (!seen.add(c.lineId())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Line " + line.getLineNo() + " appears twice");
            }
            confirmLine(to, line, c, warnings, dirtyBins);
        }

        List<TransferOrderLine> all = lines.findByTransferOrderIdOrderByLineNo(id);
        boolean done = all.stream().allMatch(l -> l.getStatus() == TransferLineStatus.CONFIRMED
                || l.getStatus() == TransferLineStatus.CANCELLED);
        if (done) {
            to.setStatus(TransferOrderStatus.COMPLETED);
            to.setCompletedAt(Instant.now());
        } else if (to.getStatus() == TransferOrderStatus.CREATED) {
            to.setStatus(TransferOrderStatus.IN_PROGRESS);
        }
        orders.save(to);
        if (!dirtyBins.isEmpty()) {
            binService.refreshWeights(dirtyBins);
        }
        BigDecimal confirmedNow = req.lines().stream().map(ConfirmLineRequest::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        audit.record("TO_CONFIRM", "TRANSFER_ORDER", String.valueOf(id), null,
                to.getToNumber() + " confirmed " + confirmedNow.stripTrailingZeros().toPlainString(), null);
        if (done) {
            refreshWave(to.getWaveId());
        }
        return new ConfirmResultDto(mapper.toDto(to, true), warnings);
    }

    private void confirmLine(TransferOrder to, TransferOrderLine line, ConfirmLineRequest c, List<String> warnings,
                             Set<Long> dirtyBins) {
        if (line.getStatus() == TransferLineStatus.CONFIRMED || line.getStatus() == TransferLineStatus.CANCELLED) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Line " + line.getLineNo() + " is already " + line.getStatus());
        }
        BigDecimal remaining = line.getPlannedQty().subtract(line.getConfirmedQty());
        if (c.quantity().compareTo(remaining) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Line " + line.getLineNo() + ": "
                    + c.quantity().stripTrailingZeros().toPlainString() + " exceeds the remaining "
                    + remaining.stripTrailingZeros().toPlainString());
        }
        Long itemId = itemId(line);

        // Scans: a scanned code that does not match the task is refused before anything moves.
        WarehouseBin fromBin = line.getFromBinId() == null ? null : bins.findById(line.getFromBinId()).orElse(null);
        if (c.scannedFromBin() != null && !c.scannedFromBin().isBlank()
                && (fromBin == null || !WmsSupport.sameCode(c.scannedFromBin(), fromBin.getBinCode()))) {
            throw new BusinessRuleException(ErrorCode.SCAN_MISMATCH, "Line " + line.getLineNo() + ": scanned bin "
                    + c.scannedFromBin() + " is not the source bin" + (fromBin == null ? "" : " " + fromBin.getBinCode()));
        }
        if (c.scannedBatch() != null && !c.scannedBatch().isBlank()) {
            String expected = stock.batchNumber(line.getItemType(), line.getBatchId()).orElse(null);
            if (expected == null || !WmsSupport.sameCode(c.scannedBatch(), expected)) {
                throw new BusinessRuleException(ErrorCode.SCAN_MISMATCH, "Line " + line.getLineNo()
                        + ": scanned batch " + c.scannedBatch() + " does not match the task's batch");
            }
        }

        // Destination: explicit choice, the planned bin, or the scanned bin code.
        WarehouseBin toBin = null;
        Long toBinId = c.toBinId() != null ? c.toBinId() : line.getToBinId();
        if (toBinId != null) {
            toBin = requireBinInWarehouse(toBinId, to.getWarehouseId());
            if (c.scannedToBin() != null && !c.scannedToBin().isBlank()
                    && !WmsSupport.sameCode(c.scannedToBin(), toBin.getBinCode())) {
                throw new BusinessRuleException(ErrorCode.SCAN_MISMATCH, "Line " + line.getLineNo()
                        + ": scanned bin " + c.scannedToBin() + " is not the destination bin " + toBin.getBinCode());
            }
        } else if (c.scannedToBin() != null && !c.scannedToBin().isBlank()) {
            toBin = bins.findActiveByWarehouse(to.getWarehouseId()).stream()
                    .filter(b -> WmsSupport.sameCode(b.getBinCode(), c.scannedToBin())).findFirst()
                    .orElseThrow(() -> new BusinessRuleException(ErrorCode.SCAN_MISMATCH,
                            "Line " + line.getLineNo() + ": no active bin " + c.scannedToBin() + " in this warehouse"));
        }

        if (toBin == null && to.getMovementType() != MovementType.PICKING) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Line " + line.getLineNo() + ": choose or scan the destination bin");
        }

        List<String> lineWarnings = new ArrayList<>();
        if (toBin != null) {
            if (toBin.getLocationId().equals(line.getFromLocationId())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Line " + line.getLineNo() + ": the stock is already in bin " + toBin.getBinCode());
            }
            Long zoneId = toBin.getZoneId();
            WarehouseZone zone = zones.findById(zoneId)
                    .orElseThrow(() -> new NotFoundException("WarehouseZone", zoneId));
            assertMovable(line, toBin, zone);
            lineWarnings.addAll(capacity.checkAndEnforce(toBin, line.getItemType(), itemId, c.quantity()));

            LocationStock source = resolveSource(line, itemId, c.quantity());
            posting.transfer(new TransferCommand(line.getItemType(),
                    line.getMaterialId(), line.getProductId(),
                    "MATERIAL".equals(line.getItemType()) ? line.getBatchId() : null,
                    "PRODUCT".equals(line.getItemType()) ? line.getBatchId() : null,
                    source.warehouseId(), line.getFromLocationId(), source.stockStatus(),
                    to.getWarehouseId(), toBin.getLocationId(), source.stockStatus(),
                    c.quantity(), stock.itemInfo(line.getItemType(), itemId).map(i -> i.uom()).orElse("PCS"),
                    "TRANSFER_ORDER", to.getId(), to.getToNumber(), "TO " + to.getToNumber() + " line " + line.getLineNo()));
            line.setStockStatus(source.stockStatus());
            line.setToBinId(toBin.getId());
            line.setToLocationId(toBin.getLocationId());
            dirtyBins.add(toBin.getId());
            if (line.getFromBinId() != null) {
                dirtyBins.add(line.getFromBinId());
            }
        }

        line.setConfirmedQty(line.getConfirmedQty().add(c.quantity()));
        line.setStatus(line.getConfirmedQty().compareTo(line.getPlannedQty()) >= 0
                ? TransferLineStatus.CONFIRMED : TransferLineStatus.PARTIAL);
        line.setConfirmedAt(Instant.now());
        line.setConfirmedBy(WmsSupport.currentUserId());
        if (!lineWarnings.isEmpty()) {
            line.setWarning(WmsSupport.clip(String.join("; ", lineWarnings), 300));
            warnings.addAll(lineWarnings);
        }
        lines.save(line);
    }

    /** The stock row to move: the line's status if it still has enough unreserved quantity, else any that does. */
    private LocationStock resolveSource(TransferOrderLine line, Long itemId, BigDecimal qty) {
        List<LocationStock> rows = stock.stockOfBatchAt(line.getFromLocationId(), line.getItemType(), itemId,
                line.getBatchId());
        return rows.stream().filter(r -> r.available().compareTo(qty) >= 0)
                .min(Comparator.comparing((LocationStock r) -> !r.stockStatus().equals(line.getStockStatus())))
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.INSUFFICIENT_STOCK,
                        "Line " + line.getLineNo() + ": less than " + qty.stripTrailingZeros().toPlainString()
                                + " unreserved stock at the source location"));
    }

    /** Stock still awaiting inspection may only go to quarantine or QC bins, never into storage. */
    private void assertMovable(TransferOrderLine line, WarehouseBin toBin, WarehouseZone zone) {
        String status = stock.batchStatus(line.getItemType(), line.getBatchId()).orElse(null);
        if (status == null) {
            return;
        }
        boolean released = "MATERIAL".equals(line.getItemType()) ? "AVAILABLE".equals(status)
                : "APPROVED".equals(status) || "PARTIALLY_APPROVED".equals(status);
        boolean holdingArea = toBin.getBinType() == BinType.QUARANTINE || toBin.getBinType() == BinType.QC
                || zone.getZoneType() == ZoneType.QC || zone.getZoneType() == ZoneType.RECEIVING;
        if (!released && !holdingArea) {
            throw new BusinessRuleException(ErrorCode.PUTAWAY_BLOCKED, "Line " + line.getLineNo() + ": the batch is "
                    + status + "; finish inspection before storing it in " + toBin.getBinCode());
        }
    }

    // ---- shared helpers -------------------------------------------------------------------------------------

    /** Marks a released wave completed once every transfer order of it has finished or been cancelled. */
    void refreshWave(Long waveId) {
        if (waveId == null) {
            return;
        }
        PickWave wave = waves.lockById(waveId).orElse(null);
        if (wave == null || wave.getStatus() != WaveStatus.RELEASED) {
            return;
        }
        boolean allClosed = orders.findByWaveId(waveId).stream().noneMatch(o -> o.getStatus().isOpen());
        if (allClosed) {
            wave.setStatus(WaveStatus.COMPLETED);
            waves.save(wave);
            audit.record("WAVE_COMPLETE", "PICK_WAVE", String.valueOf(waveId), "RELEASED", "COMPLETED", null);
        }
    }

    private static Long itemId(TransferOrderLine l) {
        return "MATERIAL".equals(l.getItemType()) ? l.getMaterialId() : l.getProductId();
    }

    private TransferOrder require(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("TransferOrder", id));
    }

    private static void requireOpen(TransferOrder to) {
        if (!to.getStatus().isOpen()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    to.getToNumber() + " is " + to.getStatus());
        }
    }

    private static BusinessRuleException illegal(TransferOrder to, TransferOrderStatus target) {
        return new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                to.getToNumber() + " is " + to.getStatus() + " and cannot become " + target);
    }

    private WarehouseBin requireBinInWarehouse(Long binId, Long warehouseId) {
        WarehouseBin b = bins.findById(binId).orElseThrow(() -> new NotFoundException("WarehouseBin", binId));
        WarehouseZone z = zones.findById(b.getZoneId()).orElseThrow(() -> new NotFoundException("WarehouseZone", b.getZoneId()));
        if (!z.getWarehouseId().equals(warehouseId)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Bin " + b.getBinCode() + " is not in this warehouse");
        }
        if (!b.isActive()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Bin " + b.getBinCode() + " is inactive");
        }
        return b;
    }

    /** Each movement type is executed by the permission that owns it; the endpoint only checks the union. */
    static void requireMovementAuthority(MovementType type) {
        String needed = switch (type) {
            case PUTAWAY, CROSS_DOCK -> "PUTAWAY_MANAGE";
            case PICKING -> "PICKING_MANAGE";
            case INTERNAL_TRANSFER, CYCLE_COUNT -> "TRANSFER_MANAGE";
        };
        if (!WmsSupport.authorities().contains(needed)) {
            throw new AccessDeniedException("Requires " + needed + " for " + type + " transfer orders");
        }
    }
}
