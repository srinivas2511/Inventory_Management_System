package com.mfg.ims.material.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ConflictException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.material.dto.MaterialDto;
import com.mfg.ims.material.dto.SaveMaterialRequest;
import com.mfg.ims.material.dto.UomDto;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.material.repository.UomRepository;
import com.mfg.ims.partner.repository.SupplierRepository;

@Service
public class MaterialService {

    private final MaterialRepository materialRepo;
    private final UomRepository uomRepo;
    private final SupplierRepository supplierRepo;
    private final AuditService auditService;
    private final NamedParameterJdbcTemplate jdbc;

    public MaterialService(MaterialRepository materialRepo, UomRepository uomRepo,
                           SupplierRepository supplierRepo, AuditService auditService,
                           NamedParameterJdbcTemplate jdbc) {
        this.materialRepo = materialRepo;
        this.uomRepo = uomRepo;
        this.supplierRepo = supplierRepo;
        this.auditService = auditService;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Page<MaterialDto> search(String q, String type, Boolean active, Pageable pageable) {
        return materialRepo.search(q, type, active, pageable).map(MaterialDto::from);
    }

    @Transactional(readOnly = true)
    public MaterialDto findById(Long id) {
        return MaterialDto.from(require(id));
    }

    @Transactional
    public MaterialDto create(SaveMaterialRequest req) {
        String code = (req.materialCode() == null || req.materialCode().isBlank())
                ? generateCode()
                : req.materialCode().trim();
        if (materialRepo.existsByMaterialCode(code)) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Material code already exists: " + code);
        }
        validateUom(req.uom());
        Material m = new Material();
        m.setMaterialCode(code);
        apply(m, req);
        materialRepo.save(m);
        auditService.record("CREATE", "MATERIAL", String.valueOf(m.getId()), null, m.getMaterialCode(), null);
        return MaterialDto.from(m);
    }

    @Transactional
    public MaterialDto update(Long id, SaveMaterialRequest req) {
        Material m = require(id);
        validateUom(req.uom());
        String old = m.getMaterialCode();
        apply(m, req);
        materialRepo.save(m);
        auditService.record("UPDATE", "MATERIAL", String.valueOf(id), old, m.getName(), null);
        return MaterialDto.from(m);
    }

    @Transactional
    public void deactivate(Long id) {
        Material m = require(id);
        if (!m.isActive()) return;
        m.setActive(false);
        materialRepo.save(m);
        auditService.record("DEACTIVATE", "MATERIAL", String.valueOf(id), "active", "inactive", null);
    }

    private void apply(Material m, SaveMaterialRequest req) {
        m.setName(req.name());
        m.setMaterialType(req.materialType() == null || req.materialType().isBlank() ? "" : req.materialType().trim());
        m.setGrade(req.grade());
        m.setDiameterMm(req.diameterMm());
        m.setUom(req.uom());

        BigDecimal minStock = req.minStock() != null ? req.minStock() : BigDecimal.ZERO;
        BigDecimal reorderLevel = req.reorderLevel() != null ? req.reorderLevel() : BigDecimal.ZERO;
        m.setMinStock(minStock);
        m.setReorderLevel(reorderLevel);
        m.setMaxStock(req.maxStock());
        m.setStandardCost(req.standardCost() != null ? req.standardCost() : BigDecimal.ZERO);
        m.setShelfLifeDays(req.shelfLifeDays());
        m.setDescription(req.description());
        m.setServiceItem(req.serviceItem());
        m.setSourceType(req.sourceType() == null || req.sourceType().isBlank() ? "LOCAL" : req.sourceType());
        m.setCustomerMaterial(req.customerMaterial());
        m.setHsnCode(req.hsnCode() == null || req.hsnCode().isBlank() ? null : req.hsnCode().trim().toUpperCase());
        m.setMinOrderQty(req.minOrderQty());

        if (req.preferredSupplierId() != null) {
            m.setPreferredSupplier(supplierRepo.findById(req.preferredSupplierId())
                .orElseThrow(() -> new NotFoundException("Supplier not found: " + req.preferredSupplierId())));
        } else {
            m.setPreferredSupplier(null);
        }

        if (minStock.compareTo(reorderLevel) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                "min_stock must be ≤ reorder_level");
        }
        if (req.maxStock() != null && reorderLevel.compareTo(req.maxStock()) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                "reorder_level must be ≤ max_stock");
        }
    }

    private String generateCode() {
        Long next = jdbc.queryForObject(
                "SELECT nextval('ims.material_code_seq')", java.util.Map.of(), Long.class);
        return String.format("MAT-%04d", next);
    }

    @Transactional(readOnly = true)
    public List<UomDto> listUoms() {
        return uomRepo.findAllByOrderByKindAscCodeAsc().stream().map(UomDto::from).toList();
    }

    private void validateUom(String uom) {
        if (!uomRepo.existsById(uom)) {
            throw new NotFoundException("UOM not found: " + uom);
        }
    }

    private Material require(Long id) {
        return materialRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Material not found: " + id));
    }
}
