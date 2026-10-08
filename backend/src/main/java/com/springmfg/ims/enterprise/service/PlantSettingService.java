package com.springmfg.ims.enterprise.service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.enterprise.domain.Plant;
import com.springmfg.ims.enterprise.dto.PlantDtos.PlantSettingDto;
import com.springmfg.ims.enterprise.dto.PlantDtos.SaveSettingRequest;
import com.springmfg.ims.enterprise.repository.PlantRepository;
import com.springmfg.ims.warehouse.repository.WarehouseRepository;

/**
 * Plant-level overrides of company defaults (ENT-005). A setting resolves plant -> company -> system default. The four
 * overridable keys and their system defaults are seeded in {@code system_settings} as {@code enterprise.<key>}.
 */
@Service
public class PlantSettingService {

    public static final List<String> KEYS =
            List.of("default_warehouse_id", "timezone", "production_calendar", "costing_method");

    private final NamedParameterJdbcTemplate jdbc;
    private final PlantRepository plantRepo;
    private final WarehouseRepository warehouseRepo;
    private final AuditService auditService;

    public PlantSettingService(NamedParameterJdbcTemplate jdbc, PlantRepository plantRepo,
                               WarehouseRepository warehouseRepo, AuditService auditService) {
        this.jdbc = jdbc;
        this.plantRepo = plantRepo;
        this.warehouseRepo = warehouseRepo;
        this.auditService = auditService;
    }

    /** The value in force for a plant, with the level it comes from. */
    @Transactional(readOnly = true)
    public Optional<PlantSettingDto> resolve(Long plantId, String key) {
        requireKey(key);
        Plant plant = plantRepo.findById(plantId).orElseThrow(() -> new NotFoundException("Plant", plantId));
        Optional<String> atPlant = override(plant.getCompanyId(), plantId, key);
        if (atPlant.isPresent()) {
            return Optional.of(new PlantSettingDto(key, atPlant.get(), "PLANT"));
        }
        Optional<String> atCompany = override(plant.getCompanyId(), null, key);
        if (atCompany.isPresent()) {
            return Optional.of(new PlantSettingDto(key, atCompany.get(), "COMPANY"));
        }
        return systemDefault(key).map(v -> new PlantSettingDto(key, v, "SYSTEM"));
    }

    /** Every overridable setting for a plant, resolved. */
    @Transactional(readOnly = true)
    public List<PlantSettingDto> effective(Long plantId) {
        List<PlantSettingDto> out = new ArrayList<>();
        for (String key : KEYS) {
            out.add(resolve(plantId, key).orElse(new PlantSettingDto(key, "", "SYSTEM")));
        }
        return out;
    }

    /** The company-level defaults (plant_id NULL) for the plant's company, resolved against the system defaults. */
    @Transactional(readOnly = true)
    public List<PlantSettingDto> companyDefaults(Long companyId) {
        List<PlantSettingDto> out = new ArrayList<>();
        for (String key : KEYS) {
            Optional<String> atCompany = override(companyId, null, key);
            out.add(atCompany.map(v -> new PlantSettingDto(key, v, "COMPANY"))
                    .orElseGet(() -> new PlantSettingDto(key, systemDefault(key).orElse(""), "SYSTEM")));
        }
        return out;
    }

    /** Sets an override for a plant, or for the company when {@code plantId} is null. */
    @Transactional
    public void set(SaveSettingRequest req) {
        requireKey(req.key());
        Long companyId = companyOf(req.plantId());
        String value = validate(req.plantId(), req.key(), req.value().trim());
        Optional<String> old = override(companyId, req.plantId(), req.key());
        if (old.isPresent()) {
            jdbc.update("""
                    UPDATE plant_settings SET setting_value = :v, updated_at = now(), version = version + 1
                     WHERE company_id = :c AND COALESCE(plant_id, 0) = :pz AND setting_key = :k
                    """, params(companyId, req.plantId(), req.key()).addValue("v", value));
        } else {
            jdbc.update("""
                    INSERT INTO plant_settings (company_id, plant_id, setting_key, setting_value)
                    VALUES (:c, NULLIF(:pz, 0), :k, :v)
                    """, params(companyId, req.plantId(), req.key()).addValue("v", value));
        }
        auditService.record("PLANT_SETTING_SET", "PLANT_SETTING", scope(req.plantId()) + ":" + req.key(),
                old.orElse(null), value, null);
    }

    /** Removes an override so the next level applies again. */
    @Transactional
    public void clear(Long plantId, String key) {
        requireKey(key);
        Long companyId = companyOf(plantId);
        Optional<String> old = override(companyId, plantId, key);
        if (old.isEmpty()) {
            return;
        }
        jdbc.update("""
                DELETE FROM plant_settings
                 WHERE company_id = :c AND COALESCE(plant_id, 0) = :pz AND setting_key = :k
                """, params(companyId, plantId, key));
        auditService.record("PLANT_SETTING_CLEAR", "PLANT_SETTING", scope(plantId) + ":" + key, old.get(), null, null);
    }

    private String validate(Long plantId, String key, String value) {
        switch (key) {
            case "costing_method" -> {
                if (!List.of("MOVING_AVERAGE", "STANDARD").contains(value)) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                            "costing_method must be MOVING_AVERAGE or STANDARD");
                }
            }
            case "timezone" -> {
                try {
                    ZoneId.of(value);
                } catch (Exception e) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Unknown time zone: " + value);
                }
            }
            case "default_warehouse_id" -> {
                long id;
                try {
                    id = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                            "default_warehouse_id must be a warehouse id");
                }
                var warehouse = warehouseRepo.findById(id)
                        .orElseThrow(() -> new NotFoundException("Warehouse", id));
                if (plantId != null && warehouse.getPlantId() != null && !plantId.equals(warehouse.getPlantId())) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                            "The default warehouse must belong to the plant");
                }
            }
            default -> {
                if (value.length() > 60) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, key + " is limited to 60 characters");
                }
            }
        }
        return value;
    }

    private Long companyOf(Long plantId) {
        if (plantId == null) {
            return plantRepo.findAll().stream().map(Plant::getCompanyId).min(Long::compare)
                    .orElseThrow(() -> new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "No plant exists"));
        }
        return plantRepo.findById(plantId).orElseThrow(() -> new NotFoundException("Plant", plantId)).getCompanyId();
    }

    private Optional<String> override(Long companyId, Long plantId, String key) {
        List<String> rows = jdbc.queryForList("""
                SELECT setting_value FROM plant_settings
                 WHERE company_id = :c AND COALESCE(plant_id, 0) = :pz AND setting_key = :k
                """, params(companyId, plantId, key), String.class);
        return rows.stream().findFirst();
    }

    private Optional<String> systemDefault(String key) {
        List<String> rows = jdbc.queryForList("SELECT value FROM system_settings WHERE key = :k",
                new MapSqlParameterSource("k", "enterprise." + key), String.class);
        return rows.stream().findFirst();
    }

    private static MapSqlParameterSource params(Long companyId, Long plantId, String key) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("c", companyId);
        m.put("pz", plantId == null ? 0L : plantId);
        m.put("k", key);
        return new MapSqlParameterSource(m);
    }

    private static String scope(Long plantId) {
        return plantId == null ? "company" : "plant-" + plantId;
    }

    private static void requireKey(String key) {
        if (!KEYS.contains(key)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Unknown setting: " + key
                    + " (allowed: " + String.join(", ", KEYS) + ")");
        }
    }
}
