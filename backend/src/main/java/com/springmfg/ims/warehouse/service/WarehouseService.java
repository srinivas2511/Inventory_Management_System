package com.springmfg.ims.warehouse.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.warehouse.domain.Warehouse;
import com.springmfg.ims.warehouse.domain.WarehouseLocation;
import com.springmfg.ims.warehouse.dto.SaveLocationRequest;
import com.springmfg.ims.warehouse.dto.SaveWarehouseRequest;
import com.springmfg.ims.warehouse.dto.WarehouseDto;
import com.springmfg.ims.warehouse.dto.WarehouseHealthDto;
import com.springmfg.ims.warehouse.dto.WarehouseLocationDto;
import com.springmfg.ims.warehouse.repository.WarehouseLocationRepository;
import com.springmfg.ims.warehouse.repository.WarehouseRepository;

@Service
public class WarehouseService {

    static final List<String> REQUIRED_LOCATION_TYPES = List.of(
            "RAW_MATERIAL", "WIP", "FINISHED", "QUARANTINE", "QUALITY_PENDING", "SCRAP");

    private final WarehouseRepository warehouseRepo;
    private final WarehouseLocationRepository locationRepo;
    private final AuditService auditService;

    public WarehouseService(WarehouseRepository warehouseRepo,
                            WarehouseLocationRepository locationRepo,
                            AuditService auditService) {
        this.warehouseRepo = warehouseRepo;
        this.locationRepo = locationRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<WarehouseDto> search(String q, Boolean active) {
        return warehouseRepo.search(q, active).stream().map(WarehouseDto::from).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseDto findById(Long id) {
        return WarehouseDto.from(require(id));
    }

    @Transactional
    public WarehouseDto create(SaveWarehouseRequest req) {
        if (warehouseRepo.existsByWarehouseCode(req.warehouseCode())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Warehouse code already exists: " + req.warehouseCode());
        }
        Warehouse w = new Warehouse();
        apply(w, req);
        warehouseRepo.save(w);
        auditService.record("CREATE", "WAREHOUSE", String.valueOf(w.getId()), null, w.getWarehouseCode(), null);
        return WarehouseDto.from(w);
    }

    @Transactional
    public WarehouseDto update(Long id, SaveWarehouseRequest req) {
        Warehouse w = require(id);
        String old = w.getWarehouseCode();
        apply(w, req);
        warehouseRepo.save(w);
        auditService.record("UPDATE", "WAREHOUSE", String.valueOf(id), old, w.getName(), null);
        return WarehouseDto.from(w);
    }

    @Transactional
    public void deactivate(Long id) {
        Warehouse w = require(id);
        if (!w.isActive()) return;
        w.setActive(false);
        warehouseRepo.save(w);
        auditService.record("DEACTIVATE", "WAREHOUSE", String.valueOf(id), "active", "inactive", null);
    }

    @Transactional(readOnly = true)
    public List<WarehouseLocationDto> listLocations(Long warehouseId, Boolean active) {
        List<WarehouseLocation> locations = (active != null && active)
                ? locationRepo.findByWarehouseIdAndActiveTrue(warehouseId)
                : locationRepo.findByWarehouseId(warehouseId);
        return locations.stream().map(WarehouseLocationDto::from).toList();
    }

    @Transactional
    public WarehouseLocationDto addLocation(Long warehouseId, SaveLocationRequest req) {
        Warehouse w = require(warehouseId);
        if (locationRepo.existsByWarehouseIdAndLocationCode(warehouseId, req.locationCode())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Location code already exists: " + req.locationCode());
        }
        WarehouseLocation loc = new WarehouseLocation();
        loc.setWarehouse(w);
        loc.setLocationCode(req.locationCode());
        loc.setName(req.name());
        loc.setLocationType(req.locationType());
        locationRepo.save(loc);
        auditService.record("CREATE", "WAREHOUSE_LOCATION", String.valueOf(loc.getId()), null, req.locationCode(), null);
        return WarehouseLocationDto.from(loc);
    }

    @Transactional
    public WarehouseLocationDto updateLocation(Long warehouseId, Long locationId, SaveLocationRequest req) {
        WarehouseLocation loc = requireLocation(warehouseId, locationId);
        loc.setName(req.name());
        loc.setLocationType(req.locationType());
        locationRepo.save(loc);
        return WarehouseLocationDto.from(loc);
    }

    @Transactional
    public void deactivateLocation(Long warehouseId, Long locationId) {
        WarehouseLocation loc = requireLocation(warehouseId, locationId);
        loc.setActive(false);
        locationRepo.save(loc);
    }

    @Transactional(readOnly = true)
    public List<WarehouseHealthDto> healthCheck() {
        return warehouseRepo.findByActiveTrue().stream().map(w -> {
            List<String> present = locationRepo.findActiveLocationTypesByWarehouse(w.getId());
            List<String> missing = REQUIRED_LOCATION_TYPES.stream()
                    .filter(t -> !present.contains(t)).toList();
            return new WarehouseHealthDto(w.getId(), w.getWarehouseCode(), missing);
        }).toList();
    }

    private void apply(Warehouse w, SaveWarehouseRequest req) {
        w.setWarehouseCode(req.warehouseCode());
        w.setName(req.name());
        w.setAddress(req.address());
    }

    private Warehouse require(Long id) {
        return warehouseRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + id));
    }

    private WarehouseLocation requireLocation(Long warehouseId, Long locationId) {
        WarehouseLocation loc = locationRepo.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found: " + locationId));
        if (!loc.getWarehouse().getId().equals(warehouseId)) {
            throw new NotFoundException("Location " + locationId + " not in warehouse " + warehouseId);
        }
        return loc;
    }
}
