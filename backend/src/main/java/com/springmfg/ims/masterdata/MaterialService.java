package com.springmfg.ims.masterdata;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.AuditCommand;
import com.springmfg.ims.common.api.PageRequests;
import com.springmfg.ims.common.api.PageResponse;
import com.springmfg.ims.common.api.Specs;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.exception.Problems;
import com.springmfg.ims.common.exception.ValidationFailedException;

/**
 * Materials (DESIGN.md section 5.2): unique code, {@code min <= reorder <= max}, a known unit of measure, an active
 * preferred supplier when one is chosen. A material is deactivated, never deleted; deactivating one that is still in
 * use is refused unless the caller confirms with {@code force} (the checks are {@link MaterialUsageCheck} beans).
 */
@Service
public class MaterialService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "materialType", "grade", "diameterMm", "uom",
            "standardCost", "active", "createdAt");

    private final MaterialRepository materials;
    private final SupplierRepository suppliers;
    private final JdbcTemplate jdbc;
    private final MasterDataMapper mapper;
    private final ApplicationEventPublisher events;
    private final List<MaterialUsageCheck> usageChecks;

    MaterialService(MaterialRepository materials, SupplierRepository suppliers, JdbcTemplate jdbc, MasterDataMapper mapper,
            ApplicationEventPublisher events, List<MaterialUsageCheck> usageChecks) {
        this.materials = materials;
        this.suppliers = suppliers;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.events = events;
        this.usageChecks = usageChecks;
    }

    @Transactional(readOnly = true)
    public PageResponse<MaterialDtos.MaterialSummary> list(String q, Boolean active, MaterialType type, Long supplierId,
            String uom, Pageable pageable) {
        Pageable page = PageRequests.restrictSort(pageable, SORTABLE, Sort.by("code"));
        Sort translated = Sort.by(page.getSort().stream()
                .map(o -> o.getProperty().equals("code") ? o.withProperty("materialCode") : o).toList());
        var spec = Specs.<Material>of().search(q, "materialCode", "name", "grade").eq("active", active)
                .eq("materialType", type).eq("preferredSupplier.id", supplierId).eq("uom", uom).build();
        return PageResponse.from(materials.findAll(spec, PageRequest.of(page.getPageNumber(), page.getPageSize(), translated)),
                mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public MaterialDtos.MaterialResponse get(long id) {
        return mapper.toResponse(find(id));
    }

    @Transactional
    public MaterialDtos.MaterialResponse create(MaterialDtos.CreateMaterialRequest request) {
        String code = request.code().trim();
        if (materials.existsByMaterialCode(code)) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Material code '" + code + "' already exists.");
        }
        Material material = new Material(code);
        apply(material, request.name(), request.materialType(), request.grade(), request.diameterMm(), request.uom(),
                request.preferredSupplierId(), request.minStock(), request.reorderLevel(), request.maxStock(),
                request.standardCost(), request.shelfLifeDays(), request.description());
        Material saved = materials.saveAndFlush(material);
        events.publishEvent(new AuditCommand("MATERIAL_CREATED", "Material", String.valueOf(saved.getId()), null, snapshot(saved), null));
        return mapper.toResponse(saved);
    }

    @Transactional
    public MaterialDtos.MaterialResponse update(long id, MaterialDtos.UpdateMaterialRequest request) {
        Material material = find(id);
        if (material.getVersion() != request.version().longValue()) {
            throw new ConflictException(ErrorCode.VERSION_CONFLICT, "The material was modified by someone else. Reload and try again.");
        }
        if (!material.getUom().equals(request.uom())) {
            List<String> blockers = usageChecks.stream().flatMap(c -> c.unitChangeBlockers(id).stream()).toList();
            if (!blockers.isEmpty()) {
                throw new BusinessRuleException(ErrorCode.UNIT_CHANGE_BLOCKED, "The unit of measure of '" + material.getMaterialCode()
                        + "' cannot change: " + String.join("; ", blockers) + ".");
            }
        }
        Map<String, Object> before = snapshot(material);
        apply(material, request.name(), request.materialType(), request.grade(), request.diameterMm(), request.uom(),
                request.preferredSupplierId(), request.minStock(), request.reorderLevel(), request.maxStock(),
                request.standardCost(), request.shelfLifeDays(), request.description());
        materials.saveAndFlush(material);
        events.publishEvent(new AuditCommand("MATERIAL_UPDATED", "Material", String.valueOf(id), before, snapshot(material), null));
        return mapper.toResponse(material);
    }

    /**
     * Deactivates the material. If anything still uses it the call is refused with 422 {@code DEACTIVATION_BLOCKED}
     * listing why, unless {@code force} is true (the warnings are then recorded in the audit entry).
     */
    @Transactional
    public void deactivate(long id, boolean force, String reason) {
        Material material = find(id);
        if (!material.isActive()) {
            return; // idempotent
        }
        List<String> warnings = usageChecks.stream().flatMap(c -> c.deactivationWarnings(id).stream()).toList();
        if (!warnings.isEmpty() && !force) {
            throw new BusinessRuleException(ErrorCode.DEACTIVATION_BLOCKED, "Material '" + material.getMaterialCode()
                    + "' is still in use: " + String.join("; ", warnings) + ". Repeat the request with force=true to deactivate it anyway.");
        }
        Map<String, Object> before = snapshot(material);
        material.setActive(false);
        materials.saveAndFlush(material);
        Map<String, Object> after = snapshot(material);
        if (!warnings.isEmpty()) {
            after.put("deactivatedDespite", warnings);
        }
        events.publishEvent(new AuditCommand("MATERIAL_DEACTIVATED", "Material", String.valueOf(id), before, after,
                PartnerService.blank(reason)));
    }

    @Transactional
    public MaterialDtos.MaterialResponse activate(long id) {
        Material material = find(id);
        if (!material.isActive()) {
            Map<String, Object> before = snapshot(material);
            material.setActive(true);
            materials.saveAndFlush(material);
            events.publishEvent(new AuditCommand("MATERIAL_ACTIVATED", "Material", String.valueOf(id), before, snapshot(material), null));
        }
        return mapper.toResponse(material);
    }

    @Transactional(readOnly = true)
    public List<MaterialDtos.UomResponse> units() {
        return jdbc.query("SELECT code, name, kind FROM uoms ORDER BY code",
                (rs, n) -> new MaterialDtos.UomResponse(rs.getString("code"), rs.getString("name"), rs.getString("kind")));
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private Material find(long id) {
        return materials.findById(id).orElseThrow(() -> new NotFoundException("Material", id));
    }

    private void apply(Material material, String name, MaterialType type, String grade, BigDecimal diameterMm, String uom,
            Long supplierId, BigDecimal minStock, BigDecimal reorderLevel, BigDecimal maxStock, BigDecimal standardCost,
            Integer shelfLifeDays, String description) {
        List<Problems.FieldError> errors = new ArrayList<>();
        BigDecimal min = minStock == null ? BigDecimal.ZERO : minStock;
        BigDecimal reorder = reorderLevel == null ? BigDecimal.ZERO : reorderLevel;
        if (reorder.compareTo(min) < 0) {
            errors.add(new Problems.FieldError("reorderLevel", "Must be at least the minimum stock (" + min.toPlainString() + ")."));
        }
        if (maxStock != null && maxStock.compareTo(reorder) < 0) {
            errors.add(new Problems.FieldError("maxStock", "Must be at least the reorder level (" + reorder.toPlainString() + ")."));
        }
        String unit = uom.trim();
        Integer known = jdbc.queryForObject("SELECT count(*) FROM uoms WHERE code = ?", Integer.class, unit);
        if (known == null || known == 0) {
            errors.add(new Problems.FieldError("uom", "Unknown unit of measure '" + unit + "'."));
        }
        Supplier supplier = material.getPreferredSupplier();
        if (supplierId == null) {
            supplier = null;
        } else if (supplier == null || !supplier.getId().equals(supplierId)) {
            supplier = suppliers.findById(supplierId).orElse(null);
            if (supplier == null) {
                errors.add(new Problems.FieldError("preferredSupplierId", "Unknown supplier " + supplierId + "."));
            } else if (!supplier.isActive()) {
                errors.add(new Problems.FieldError("preferredSupplierId", "Supplier '" + supplier.getCode() + "' is inactive."));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        material.update(name.trim(), type, PartnerService.blank(grade), diameterMm, unit, supplier, min, reorder, maxStock,
                standardCost == null ? BigDecimal.ZERO : standardCost, shelfLifeDays, PartnerService.blank(description));
    }

    private static Map<String, Object> snapshot(Material m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", m.getMaterialCode());
        map.put("name", m.getName());
        map.put("materialType", m.getMaterialType().name());
        map.put("grade", m.getGrade());
        map.put("diameterMm", m.getDiameterMm());
        map.put("uom", m.getUom());
        map.put("preferredSupplier", m.getPreferredSupplier() == null ? null : m.getPreferredSupplier().getCode());
        map.put("minStock", m.getMinStock());
        map.put("reorderLevel", m.getReorderLevel());
        map.put("maxStock", m.getMaxStock());
        map.put("standardCost", m.getStandardCost());
        map.put("shelfLifeDays", m.getShelfLifeDays());
        map.put("description", m.getDescription());
        map.put("active", m.isActive());
        return map;
    }
}
