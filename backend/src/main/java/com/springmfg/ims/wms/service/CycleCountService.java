package com.springmfg.ims.wms.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.inventory.dto.StockAdjustmentDto;
import com.springmfg.ims.inventory.service.StockAdjustmentService;
import com.springmfg.ims.inventory.service.StockLocationQueryService;
import com.springmfg.ims.inventory.service.StockLocationQueryService.LocationStock;
import com.springmfg.ims.warehouse.repository.WarehouseRepository;
import com.springmfg.ims.wms.domain.CycleCount;
import com.springmfg.ims.wms.domain.CycleCountLine;
import com.springmfg.ims.wms.domain.WarehouseBin;
import com.springmfg.ims.wms.domain.WarehouseZone;
import com.springmfg.ims.wms.domain.WmsEnums.CycleCountStatus;
import com.springmfg.ims.wms.domain.WmsEnums.CycleCountType;
import com.springmfg.ims.wms.dto.WmsDtos.CountEntry;
import com.springmfg.ims.wms.dto.WmsDtos.CreateCycleCountRequest;
import com.springmfg.ims.wms.dto.WmsDtos.CycleCountDto;
import com.springmfg.ims.wms.dto.WmsDtos.CycleCountLineDto;
import com.springmfg.ims.wms.dto.WmsDtos.RecordCountsRequest;
import com.springmfg.ims.wms.repository.CycleCountLineRepository;
import com.springmfg.ims.wms.repository.CycleCountRepository;
import com.springmfg.ims.wms.repository.WarehouseBinRepository;
import com.springmfg.ims.wms.repository.WarehouseZoneRepository;

/**
 * Cycle counts by zone, ABC class or random sample (WMS-007). A count snapshots stock, is counted blind, reviewed
 * against current stock, and posted by raising stock adjustments that follow the existing approval workflow; the
 * count itself never changes stock.
 */
@Service
public class CycleCountService {

    private final CycleCountRepository counts;
    private final CycleCountLineRepository countLines;
    private final WarehouseBinRepository bins;
    private final WarehouseZoneRepository zones;
    private final WarehouseRepository warehouses;
    private final StockLocationQueryService stock;
    private final StockAdjustmentService adjustments;
    private final DocumentNumberService numbers;
    private final SystemSettingService settings;
    private final AuditService audit;

    public CycleCountService(CycleCountRepository counts, CycleCountLineRepository countLines,
                             WarehouseBinRepository bins, WarehouseZoneRepository zones,
                             WarehouseRepository warehouses, StockLocationQueryService stock,
                             StockAdjustmentService adjustments, DocumentNumberService numbers,
                             SystemSettingService settings, AuditService audit) {
        this.counts = counts;
        this.countLines = countLines;
        this.bins = bins;
        this.zones = zones;
        this.warehouses = warehouses;
        this.stock = stock;
        this.adjustments = adjustments;
        this.numbers = numbers;
        this.settings = settings;
        this.audit = audit;
    }

    // ---- queries -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<CycleCountDto> search(CycleCountStatus status, Long warehouseId, Pageable pageable) {
        return counts.search(status, warehouseId, pageable).map(c -> toDto(c, false));
    }

    @Transactional(readOnly = true)
    public CycleCountDto get(Long id) {
        return toDto(require(id), true);
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------

    @Transactional
    public CycleCountDto create(CreateCycleCountRequest req) {
        warehouses.findById(req.warehouseId()).orElseThrow(() -> new NotFoundException("Warehouse", req.warehouseId()));
        List<LocationStock> all = stock.stockInWarehouse(req.warehouseId());
        Map<Long, WarehouseBin> binByLocation = bins.findByLocationIdIn(
                all.stream().map(LocationStock::locationId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(WarehouseBin::getLocationId, Function.identity()));

        List<LocationStock> scope;
        String abc = null;
        switch (req.countType()) {
            case ZONE -> {
                if (req.zoneId() == null) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Choose the zone to count");
                }
                WarehouseZone zone = zones.findById(req.zoneId()).orElseThrow(() -> new NotFoundException("WarehouseZone", req.zoneId()));
                if (!zone.getWarehouseId().equals(req.warehouseId())) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "The zone is not in this warehouse");
                }
                scope = all.stream().filter(s -> {
                    WarehouseBin b = binByLocation.get(s.locationId());
                    return b != null && b.getZoneId().equals(zone.getId());
                }).toList();
            }
            case ABC -> {
                abc = req.abcClass() == null ? null : req.abcClass().trim().toUpperCase();
                if (abc == null || !List.of("A", "B", "C").contains(abc)) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "ABC class must be A, B or C");
                }
                Map<String, String> classes = classify(req.warehouseId(), all);
                String wanted = abc;
                scope = all.stream().filter(s -> wanted.equals(classes.get(key(s)))).toList();
            }
            case RANDOM -> {
                if (req.sampleSize() == null || req.sampleSize() < 1) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Give a sample size of at least 1");
                }
                List<LocationStock> shuffled = new ArrayList<>(all);
                Collections.shuffle(shuffled);
                scope = shuffled.stream().limit(req.sampleSize()).toList();
            }
            default -> throw new IllegalStateException("Unhandled count type " + req.countType());
        }
        if (scope.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "No stock matches this count scope");
        }

        CycleCount cc = new CycleCount();
        cc.setCountNumber(numbers.next("CC"));
        cc.setWarehouseId(req.warehouseId());
        cc.setCountType(req.countType());
        cc.setZoneId(req.countType() == CycleCountType.ZONE ? req.zoneId() : null);
        cc.setAbcClass(abc);
        cc.setSampleSize(req.countType() == CycleCountType.RANDOM ? req.sampleSize() : null);
        cc.setStatus(CycleCountStatus.CREATED);
        cc.setNotes(WmsSupport.clip(req.notes(), 300));
        cc = counts.save(cc);

        int lineNo = 1;
        for (LocationStock s : scope) {
            CycleCountLine l = new CycleCountLine();
            l.setCycleCountId(cc.getId());
            l.setLineNo(lineNo++);
            WarehouseBin bin = binByLocation.get(s.locationId());
            l.setBinId(bin == null ? null : bin.getId());
            l.setLocationId(s.locationId());
            l.setItemType(s.itemType());
            l.setMaterialId(s.materialId());
            l.setProductId(s.productId());
            l.setBatchId(s.batchId());
            l.setStockStatus(s.stockStatus());
            l.setSystemQty(s.qtyOnHand());
            countLines.save(l);
        }
        audit.record("CYCLE_COUNT_CREATE", "CYCLE_COUNT", String.valueOf(cc.getId()), null,
                cc.getCountNumber() + " " + cc.getCountType() + " (" + scope.size() + " lines)", null);
        return toDto(cc, true);
    }

    @Transactional
    public CycleCountDto recordCounts(Long id, RecordCountsRequest req) {
        CycleCount cc = counts.lockById(id).orElseThrow(() -> new NotFoundException("CycleCount", id));
        if (!cc.getStatus().isCountable()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    cc.getCountNumber() + " is " + cc.getStatus() + " and cannot take counts");
        }
        Map<Long, CycleCountLine> byId = countLines.findByCycleCountIdOrderByLineNo(id).stream()
                .collect(Collectors.toMap(CycleCountLine::getId, Function.identity()));
        for (CountEntry e : req.lines()) {
            CycleCountLine l = byId.get(e.lineId());
            if (l == null) {
                throw new NotFoundException("CycleCountLine", e.lineId());
            }
            l.setCountedQty(e.countedQty());
            countLines.save(l);
        }
        if (cc.getStatus() == CycleCountStatus.CREATED) {
            cc.setStatus(CycleCountStatus.IN_PROGRESS);
            counts.save(cc);
        }
        return toDto(cc, true);
    }

    /** Ends counting: every line must be counted; variances are recomputed against stock as it is now. */
    @Transactional
    public CycleCountDto review(Long id) {
        CycleCount cc = counts.lockById(id).orElseThrow(() -> new NotFoundException("CycleCount", id));
        if (!cc.getStatus().canMoveTo(CycleCountStatus.REVIEW)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    cc.getCountNumber() + " is " + cc.getStatus() + " and cannot go to review");
        }
        List<CycleCountLine> ls = countLines.findByCycleCountIdOrderByLineNo(id);
        long uncounted = ls.stream().filter(l -> l.getCountedQty() == null).count();
        if (uncounted > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, uncounted + " line(s) are not counted yet");
        }
        for (CycleCountLine l : ls) {
            BigDecimal current = currentQty(l);
            l.setSystemQty(current);
            l.setVarianceQty(l.getCountedQty().subtract(current));
            countLines.save(l);
        }
        cc.setStatus(CycleCountStatus.REVIEW);
        cc.setReviewedAt(Instant.now());
        counts.save(cc);
        audit.record("CYCLE_COUNT_REVIEW", "CYCLE_COUNT", String.valueOf(id), "IN_PROGRESS", "REVIEW", null);
        return toDto(cc, true);
    }

    /** Sends a reviewed count back for a recount. */
    @Transactional
    public CycleCountDto reopen(Long id) {
        CycleCount cc = counts.lockById(id).orElseThrow(() -> new NotFoundException("CycleCount", id));
        if (cc.getStatus() != CycleCountStatus.REVIEW) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    cc.getCountNumber() + " is " + cc.getStatus() + "; only a count in review can be reopened");
        }
        cc.setStatus(CycleCountStatus.IN_PROGRESS);
        counts.save(cc);
        audit.record("CYCLE_COUNT_REOPEN", "CYCLE_COUNT", String.valueOf(id), "REVIEW", "IN_PROGRESS", null);
        return toDto(cc, true);
    }

    /** Raises one stock adjustment per variance line; each goes through the normal approval steps. */
    @Transactional
    public CycleCountDto post(Long id) {
        CycleCount cc = counts.lockById(id).orElseThrow(() -> new NotFoundException("CycleCount", id));
        if (!cc.getStatus().canMoveTo(CycleCountStatus.POSTED) || cc.getStatus() != CycleCountStatus.REVIEW) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    cc.getCountNumber() + " is " + cc.getStatus() + "; review it before posting");
        }
        int raised = 0;
        for (CycleCountLine l : countLines.findByCycleCountIdOrderByLineNo(id)) {
            if (l.getVarianceQty() == null || l.getVarianceQty().signum() == 0) {
                continue;
            }
            StockAdjustmentDto adj = adjustments.request(l.getItemType(), l.getMaterialId(), l.getProductId(),
                    l.getBatchId(), cc.getWarehouseId(), l.getLocationId(), l.getStockStatus(), l.getCountedQty(),
                    "Cycle count " + cc.getCountNumber());
            l.setAdjustmentId(adj.id());
            countLines.save(l);
            raised++;
        }
        cc.setStatus(CycleCountStatus.POSTED);
        cc.setPostedAt(Instant.now());
        counts.save(cc);
        audit.record("CYCLE_COUNT_POST", "CYCLE_COUNT", String.valueOf(id), "REVIEW", "POSTED",
                raised + " adjustment(s) raised");
        return toDto(cc, true);
    }

    @Transactional
    public CycleCountDto cancel(Long id, String reason) {
        CycleCount cc = counts.lockById(id).orElseThrow(() -> new NotFoundException("CycleCount", id));
        if (!cc.getStatus().canMoveTo(CycleCountStatus.CANCELLED)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    cc.getCountNumber() + " is " + cc.getStatus() + " and cannot be cancelled");
        }
        CycleCountStatus old = cc.getStatus();
        cc.setStatus(CycleCountStatus.CANCELLED);
        counts.save(cc);
        audit.record("CYCLE_COUNT_CANCEL", "CYCLE_COUNT", String.valueOf(id), old.name(), "CANCELLED", reason);
        return toDto(cc, true);
    }

    // ---- ABC classification ------------------------------------------------------------------------------------

    /**
     * Classifies the items of a warehouse by consumption value over the look-back window: items are ranked by value
     * and class A runs until the cumulative share reaches the A threshold (the item that crosses it is still A),
     * class B until the B threshold, and everything else, including items with no consumption, is C.
     */
    Map<String, String> classify(Long warehouseId, List<LocationStock> stockRows) {
        int days = settings.getInt("wms.cycle_count.abc_lookback_days", 365);
        BigDecimal aPct = settings.getDecimal("wms.cycle_count.abc_a_pct", new BigDecimal("80"));
        BigDecimal bPct = settings.getDecimal("wms.cycle_count.abc_b_pct", new BigDecimal("95"));
        Map<String, BigDecimal> value = stock.consumptionValueSince(Instant.now().minus(Duration.ofDays(days)), warehouseId);
        return classifyByValue(value, stockRows.stream().map(CycleCountService::key).distinct().toList(), aPct, bPct);
    }

    static Map<String, String> classifyByValue(Map<String, BigDecimal> value, List<String> itemKeys,
                                               BigDecimal aPct, BigDecimal bPct) {
        BigDecimal total = itemKeys.stream().map(k -> value.getOrDefault(k, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, String> out = new HashMap<>();
        List<String> ranked = itemKeys.stream()
                .sorted(Comparator.comparing((String k) -> value.getOrDefault(k, BigDecimal.ZERO)).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        BigDecimal cumulative = BigDecimal.ZERO;
        for (String k : ranked) {
            BigDecimal v = value.getOrDefault(k, BigDecimal.ZERO);
            if (total.signum() == 0 || v.signum() == 0) {
                out.put(k, "C");
                continue;
            }
            BigDecimal before = cumulative.multiply(BigDecimal.valueOf(100)).divide(total, 6, java.math.RoundingMode.HALF_UP);
            cumulative = cumulative.add(v);
            out.put(k, before.compareTo(aPct) < 0 ? "A" : before.compareTo(bPct) < 0 ? "B" : "C");
        }
        return out;
    }

    private static String key(LocationStock s) {
        return ("MATERIAL".equals(s.itemType()) ? "M:" : "P:") + s.itemId();
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private BigDecimal currentQty(CycleCountLine l) {
        Long itemId = "MATERIAL".equals(l.getItemType()) ? l.getMaterialId() : l.getProductId();
        Optional<LocationStock> row = stock.stockOfBatchAt(l.getLocationId(), l.getItemType(), itemId, l.getBatchId())
                .stream().filter(r -> r.stockStatus().equals(l.getStockStatus())).findFirst();
        return row.map(LocationStock::qtyOnHand).orElse(BigDecimal.ZERO);
    }

    private CycleCount require(Long id) {
        return counts.findById(id).orElseThrow(() -> new NotFoundException("CycleCount", id));
    }

    private CycleCountDto toDto(CycleCount cc, boolean withLines) {
        List<CycleCountLine> ls = countLines.findByCycleCountIdOrderByLineNo(cc.getId());
        boolean blind = cc.getStatus() == CycleCountStatus.CREATED || cc.getStatus() == CycleCountStatus.IN_PROGRESS;
        int counted = (int) ls.stream().filter(l -> l.getCountedQty() != null).count();
        int variances = (int) ls.stream().filter(l -> l.getVarianceQty() != null && l.getVarianceQty().signum() != 0).count();
        List<CycleCountLineDto> lineDtos = List.of();
        if (withLines) {
            Map<Long, String> binCodes = bins.findByIdIn(ls.stream().map(CycleCountLine::getBinId)
                    .filter(java.util.Objects::nonNull).collect(Collectors.toSet())).stream()
                    .collect(Collectors.toMap(WarehouseBin::getId, WarehouseBin::getBinCode));
            lineDtos = ls.stream().map(l -> {
                Long itemId = "MATERIAL".equals(l.getItemType()) ? l.getMaterialId() : l.getProductId();
                var info = stock.itemInfo(l.getItemType(), itemId);
                return new CycleCountLineDto(l.getId(), l.getLineNo(), l.getBinId(), binCodes.get(l.getBinId()),
                        l.getLocationId(), l.getItemType(), itemId, info.map(i -> i.code()).orElse(null),
                        info.map(i -> i.name()).orElse(null), l.getBatchId(),
                        stock.batchNumber(l.getItemType(), l.getBatchId()).orElse(null), l.getStockStatus(),
                        blind ? null : l.getSystemQty(), l.getCountedQty(), blind ? null : l.getVarianceQty(),
                        l.getAdjustmentId());
            }).toList();
        }
        return new CycleCountDto(cc.getId(), cc.getCountNumber(), cc.getWarehouseId(), cc.getCountType(),
                cc.getZoneId(), cc.getAbcClass(), cc.getSampleSize(), cc.getStatus(), cc.getNotes(),
                cc.getReviewedAt(), cc.getPostedAt(), ls.size(), counted, blind ? 0 : variances, cc.getVersion(),
                lineDtos, allowedActions(cc));
    }

    private static List<String> allowedActions(CycleCount cc) {
        return switch (cc.getStatus()) {
            case CREATED -> List.of("COUNT", "CANCEL");
            case IN_PROGRESS -> List.of("COUNT", "REVIEW", "CANCEL");
            case REVIEW -> List.of("REOPEN", "POST", "CANCEL");
            case POSTED, CANCELLED -> List.of();
        };
    }
}
