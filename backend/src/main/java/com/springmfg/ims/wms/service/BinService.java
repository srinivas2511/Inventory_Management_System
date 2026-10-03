package com.springmfg.ims.wms.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.search.SearchText;
import com.springmfg.ims.inventory.service.StockLocationQueryService;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.warehouse.domain.Warehouse;
import com.springmfg.ims.warehouse.domain.WarehouseLocation;
import com.springmfg.ims.warehouse.repository.WarehouseLocationRepository;
import com.springmfg.ims.warehouse.repository.WarehouseRepository;
import com.springmfg.ims.wms.domain.BinItemAssignment;
import com.springmfg.ims.wms.domain.PutAwayStrategyRule;
import com.springmfg.ims.wms.domain.WarehouseBin;
import com.springmfg.ims.wms.domain.WarehouseZone;
import com.springmfg.ims.wms.domain.WmsEnums.BinType;
import com.springmfg.ims.wms.domain.WmsEnums.ZoneType;
import com.springmfg.ims.wms.dto.WmsDtos.BinAssignmentDto;
import com.springmfg.ims.wms.dto.WmsDtos.BinDto;
import com.springmfg.ims.wms.dto.WmsDtos.BinStockLineDto;
import com.springmfg.ims.wms.dto.WmsDtos.PutAwayRuleDto;
import com.springmfg.ims.wms.dto.WmsDtos.SaveAssignmentRequest;
import com.springmfg.ims.wms.dto.WmsDtos.SaveBinRequest;
import com.springmfg.ims.wms.dto.WmsDtos.SavePutAwayRuleRequest;
import com.springmfg.ims.wms.dto.WmsDtos.SaveZoneRequest;
import com.springmfg.ims.wms.dto.WmsDtos.ZoneDto;
import com.springmfg.ims.wms.repository.BinItemAssignmentRepository;
import com.springmfg.ims.wms.repository.PutAwayStrategyRuleRepository;
import com.springmfg.ims.wms.repository.WarehouseBinRepository;
import com.springmfg.ims.wms.repository.WarehouseZoneRepository;

/** Zones, bins, item-to-bin assignments and put-away rules (WMS-001, WMS-002, WMS-004). */
@Service
public class BinService {

    private static final Set<String> LOCATION_TYPES = Set.of("RAW_MATERIAL", "WIP", "FINISHED", "QUARANTINE",
            "QUALITY_PENDING", "SCRAP", "DISPATCH");

    private final WarehouseZoneRepository zones;
    private final WarehouseBinRepository bins;
    private final BinItemAssignmentRepository assignments;
    private final PutAwayStrategyRuleRepository rules;
    private final WarehouseRepository warehouses;
    private final WarehouseLocationRepository locations;
    private final MaterialRepository materials;
    private final ProductRepository products;
    private final StockLocationQueryService stock;
    private final AuditService audit;

    public BinService(WarehouseZoneRepository zones, WarehouseBinRepository bins,
                      BinItemAssignmentRepository assignments, PutAwayStrategyRuleRepository rules,
                      WarehouseRepository warehouses, WarehouseLocationRepository locations,
                      MaterialRepository materials, ProductRepository products, StockLocationQueryService stock,
                      AuditService audit) {
        this.zones = zones;
        this.bins = bins;
        this.assignments = assignments;
        this.rules = rules;
        this.warehouses = warehouses;
        this.locations = locations;
        this.materials = materials;
        this.products = products;
        this.stock = stock;
        this.audit = audit;
    }

    // ---- zones ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ZoneDto> listZones(Long warehouseId) {
        return zones.findByWarehouseIdOrderByZoneCode(warehouseId).stream().map(this::toDto).toList();
    }

    @Transactional
    public ZoneDto createZone(SaveZoneRequest req) {
        requireWarehouse(req.warehouseId());
        String code = req.zoneCode().trim().toUpperCase();
        if (zones.findByWarehouseIdAndZoneCode(req.warehouseId(), code).isPresent()) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Zone " + code + " already exists in this warehouse");
        }
        WarehouseZone z = new WarehouseZone();
        z.setWarehouseId(req.warehouseId());
        z.setZoneCode(code);
        z.setName(req.name().trim());
        z.setZoneType(req.zoneType());
        z.setActive(req.active() == null || req.active());
        z = zones.save(z);
        audit.record("ZONE_CREATE", "WAREHOUSE_ZONE", String.valueOf(z.getId()), null, code, null);
        return toDto(z);
    }

    @Transactional
    public ZoneDto updateZone(Long id, SaveZoneRequest req) {
        WarehouseZone z = requireZone(id);
        checkVersion(z.getVersion(), req.version());
        boolean wasActive = z.isActive();
        boolean active = req.active() == null || req.active();
        if (wasActive && !active && bins.countActiveInZone(id) > 0) {
            throw new ConflictException(ErrorCode.DEACTIVATION_BLOCKED, "Zone " + z.getZoneCode() + " still has active bins");
        }
        z.setName(req.name().trim());
        z.setZoneType(req.zoneType());
        z.setActive(active);
        z = zones.save(z);
        audit.record("ZONE_UPDATE", "WAREHOUSE_ZONE", String.valueOf(id), String.valueOf(wasActive),
                String.valueOf(active), null);
        return toDto(z);
    }

    // ---- bins ----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<BinDto> searchBins(Long warehouseId, Long zoneId, Boolean active, String q, Pageable pageable) {
        Page<WarehouseBin> page = bins.search(warehouseId, zoneId, active, SearchText.normalise(q), pageable);
        Set<Long> occupied = stock.occupiedLocations(page.getContent().stream().map(WarehouseBin::getLocationId).toList());
        Map<Long, WarehouseZone> zoneMap = zoneMap(page.getContent().stream().map(WarehouseBin::getZoneId).toList());
        return page.map(b -> toDto(b, zoneMap.get(b.getZoneId()), occupied.contains(b.getLocationId())));
    }

    @Transactional(readOnly = true)
    public BinDto getBin(Long id) {
        WarehouseBin b = requireBin(id);
        return toDto(b, requireZone(b.getZoneId()), stock.hasStock(b.getLocationId()));
    }

    @Transactional
    public BinDto createBin(SaveBinRequest req) {
        WarehouseZone zone = requireZone(req.zoneId());
        if (!zone.isActive()) {
            throw new ConflictException(ErrorCode.DEACTIVATION_BLOCKED, "Zone " + zone.getZoneCode() + " is inactive");
        }
        String code = req.binCode().trim().toUpperCase();
        Warehouse warehouse = requireWarehouse(zone.getWarehouseId());
        if (locations.existsByWarehouseIdAndLocationCode(warehouse.getId(), code)) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY,
                    "Bin or location code " + code + " already exists in this warehouse");
        }
        String locationType = locationTypeFor(zone.getZoneType(), req.locationType());

        WarehouseLocation location = new WarehouseLocation();
        location.setWarehouse(warehouse);
        location.setLocationCode(code);
        location.setName("Bin " + code);
        location.setLocationType(locationType);
        location.setActive(true);
        location = locations.save(location);

        WarehouseBin b = new WarehouseBin();
        b.setZoneId(zone.getId());
        b.setLocationId(location.getId());
        b.setBinCode(code);
        apply(b, req);
        b.setActive(req.active() == null || req.active());
        b = bins.save(b);
        audit.record("BIN_CREATE", "WAREHOUSE_BIN", String.valueOf(b.getId()), null, code, null);
        return toDto(b, zone, false);
    }

    @Transactional
    public BinDto updateBin(Long id, SaveBinRequest req) {
        WarehouseBin b = requireBin(id);
        checkVersion(b.getVersion(), req.version());
        if (!b.getZoneId().equals(req.zoneId()) || !b.getBinCode().equalsIgnoreCase(req.binCode().trim())) {
            throw new com.springmfg.ims.common.exception.BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A bin's code and zone cannot change: its code is also its ledger location");
        }
        boolean wasActive = b.isActive();
        boolean active = req.active() == null || req.active();
        Long locationId = b.getLocationId();
        WarehouseLocation location = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("WarehouseLocation", locationId));
        if (wasActive && !active && stock.hasStock(b.getLocationId())) {
            throw new ConflictException(ErrorCode.DEACTIVATION_BLOCKED,
                    "Bin " + b.getBinCode() + " still holds stock; move it first");
        }
        apply(b, req);
        b.setActive(active);
        location.setActive(active);
        locations.save(location);
        b = bins.save(b);
        audit.record("BIN_UPDATE", "WAREHOUSE_BIN", String.valueOf(id), String.valueOf(wasActive),
                String.valueOf(active), null);
        return toDto(b, requireZone(b.getZoneId()), stock.hasStock(b.getLocationId()));
    }

    @Transactional(readOnly = true)
    public List<BinStockLineDto> binStock(Long id) {
        WarehouseBin b = requireBin(id);
        return stock.stockAtLocations(List.of(b.getLocationId())).stream().map(s -> {
            var info = stock.itemInfo(s.itemType(), s.itemId());
            return new BinStockLineDto(s.itemType(), s.itemId(), info.map(i -> i.code()).orElse(null),
                    info.map(i -> i.name()).orElse(null), s.batchId(),
                    stock.batchNumber(s.itemType(), s.batchId()).orElse(null), s.stockStatus(), s.qtyOnHand(),
                    s.qtyReserved(), info.map(i -> i.uom()).orElse(null));
        }).toList();
    }

    // ---- fixed-bin assignments ----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BinAssignmentDto> listAssignments(Long binId) {
        WarehouseBin b = requireBin(binId);
        return assignments.findByBinId(binId).stream().map(a -> toDto(a, b.getBinCode())).toList();
    }

    @Transactional
    public BinAssignmentDto addAssignment(Long binId, SaveAssignmentRequest req) {
        WarehouseBin b = requireBin(binId);
        if ((req.materialId() == null) == (req.productId() == null)) {
            throw new com.springmfg.ims.common.exception.BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Give either a material or a product");
        }
        if (req.materialId() != null && !materials.existsById(req.materialId())) {
            throw new NotFoundException("Material", req.materialId());
        }
        if (req.productId() != null && !products.existsById(req.productId())) {
            throw new NotFoundException("Product", req.productId());
        }
        if (assignments.findExisting(binId, req.materialId(), req.productId()).isPresent()) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "This item is already assigned to bin " + b.getBinCode());
        }
        BinItemAssignment a = new BinItemAssignment();
        a.setBinId(binId);
        a.setMaterialId(req.materialId());
        a.setProductId(req.productId());
        a.setPrimary(req.primary() == null || req.primary());
        a = assignments.save(a);
        audit.record("BIN_ASSIGN", "WAREHOUSE_BIN", String.valueOf(binId), null,
                req.materialId() != null ? "M:" + req.materialId() : "P:" + req.productId(), null);
        return toDto(a, b.getBinCode());
    }

    @Transactional
    public void removeAssignment(Long binId, Long assignmentId) {
        BinItemAssignment a = assignments.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("BinItemAssignment", assignmentId));
        if (!a.getBinId().equals(binId)) {
            throw new NotFoundException("BinItemAssignment", assignmentId);
        }
        assignments.delete(a);
        audit.record("BIN_UNASSIGN", "WAREHOUSE_BIN", String.valueOf(binId),
                a.getMaterialId() != null ? "M:" + a.getMaterialId() : "P:" + a.getProductId(), null, null);
    }

    // ---- put-away rules -----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PutAwayRuleDto> listRules(Long warehouseId) {
        return rules.findByWarehouseIdOrderByMaterialType(warehouseId).stream().map(BinService::toDto).toList();
    }

    @Transactional
    public PutAwayRuleDto saveRule(SavePutAwayRuleRequest req) {
        requireWarehouse(req.warehouseId());
        String type = req.materialType() == null || req.materialType().isBlank() ? null : req.materialType().trim();
        Optional<PutAwayStrategyRule> existing = type == null
                ? rules.findByWarehouseIdAndMaterialTypeIsNull(req.warehouseId())
                : rules.findByWarehouseIdAndMaterialType(req.warehouseId(), type);
        PutAwayStrategyRule r = existing.orElseGet(PutAwayStrategyRule::new);
        String old = existing.map(e -> e.getStrategy().name()).orElse(null);
        r.setWarehouseId(req.warehouseId());
        r.setMaterialType(type);
        r.setStrategy(req.strategy());
        r = rules.save(r);
        audit.record("PUTAWAY_RULE_SAVE", "PUTAWAY_RULE", String.valueOf(r.getId()), old, req.strategy().name(), null);
        return toDto(r);
    }

    @Transactional
    public void deleteRule(Long id) {
        PutAwayStrategyRule r = rules.findById(id).orElseThrow(() -> new NotFoundException("PutAwayStrategyRule", id));
        rules.delete(r);
        audit.record("PUTAWAY_RULE_DELETE", "PUTAWAY_RULE", String.valueOf(id), r.getStrategy().name(), null, null);
    }

    // ---- shared with the other warehouse services ----------------------------------------------------------------

    /** Recomputes the cached weight of the bins from the ledger. */
    @Transactional
    public void refreshWeights(Set<Long> binIds) {
        for (WarehouseBin b : bins.findByIdIn(binIds)) {
            b.setCurrentWeightKg(stock.locationWeightKg(b.getLocationId()));
            bins.save(b);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private void apply(WarehouseBin b, SaveBinRequest req) {
        b.setAisle(blankToNull(req.aisle()));
        b.setRack(blankToNull(req.rack()));
        b.setLevel(blankToNull(req.level()));
        b.setBinPosition(blankToNull(req.binPosition()));
        b.setBinType(req.binType() == null ? BinType.STANDARD : req.binType());
        b.setMaxWeightKg(req.maxWeightKg());
        b.setMaxVolumeM3(req.maxVolumeM3());
        b.setAllowedMaterialTypes(blankToNull(req.allowedMaterialTypes()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String locationTypeFor(ZoneType zoneType, String requested) {
        if (requested != null && !requested.isBlank()) {
            String t = requested.trim().toUpperCase();
            if (!LOCATION_TYPES.contains(t)) {
                throw new com.springmfg.ims.common.exception.BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Unknown location type " + requested);
            }
            return t;
        }
        return switch (zoneType) {
            case BULK -> "RAW_MATERIAL";
            case PICKING -> "FINISHED";
            case RECEIVING -> "QUARANTINE";
            case DISPATCH, STAGING -> "DISPATCH";
            case QC -> "QUALITY_PENDING";
        };
    }

    private static void checkVersion(Long current, Long requested) {
        if (requested != null && !requested.equals(current)) {
            throw new ConflictException(ErrorCode.VERSION_CONFLICT, "Changed by someone else; reload and try again");
        }
    }

    private Warehouse requireWarehouse(Long id) {
        return warehouses.findById(id).orElseThrow(() -> new NotFoundException("Warehouse", id));
    }

    private WarehouseZone requireZone(Long id) {
        return zones.findById(id).orElseThrow(() -> new NotFoundException("WarehouseZone", id));
    }

    private WarehouseBin requireBin(Long id) {
        return bins.findById(id).orElseThrow(() -> new NotFoundException("WarehouseBin", id));
    }

    private Map<Long, WarehouseZone> zoneMap(List<Long> ids) {
        return zones.findByIdIn(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(WarehouseZone::getId, Function.identity()));
    }

    private ZoneDto toDto(WarehouseZone z) {
        return new ZoneDto(z.getId(), z.getWarehouseId(), z.getZoneCode(), z.getName(), z.getZoneType(), z.isActive(),
                bins.findByZoneIdOrderByBinCode(z.getId()).size(), z.getVersion());
    }

    private BinDto toDto(WarehouseBin b, WarehouseZone z, boolean occupied) {
        return new BinDto(b.getId(), b.getZoneId(), z.getZoneCode(), z.getZoneType(), z.getWarehouseId(),
                b.getLocationId(), b.getBinCode(), b.getAisle(), b.getRack(), b.getLevel(), b.getBinPosition(),
                b.getBinType(), b.getMaxWeightKg(), b.getMaxVolumeM3(), b.getCurrentWeightKg(),
                b.getAllowedMaterialTypes(), b.isActive(), occupied, b.getVersion());
    }

    private BinAssignmentDto toDto(BinItemAssignment a, String binCode) {
        String type = a.getMaterialId() != null ? "MATERIAL" : "PRODUCT";
        Long itemId = a.getMaterialId() != null ? a.getMaterialId() : a.getProductId();
        var info = stock.itemInfo(type, itemId);
        return new BinAssignmentDto(a.getId(), a.getBinId(), binCode, a.getMaterialId(), a.getProductId(),
                info.map(i -> i.code()).orElse(null), info.map(i -> i.name()).orElse(null), a.isPrimary());
    }

    private static PutAwayRuleDto toDto(PutAwayStrategyRule r) {
        return new PutAwayRuleDto(r.getId(), r.getWarehouseId(), r.getMaterialType(), r.getStrategy());
    }
}
