package com.springmfg.ims.material.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.material.dto.MaterialDto;
import com.springmfg.ims.material.dto.SaveMaterialRequest;
import com.springmfg.ims.material.dto.UomDto;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.material.repository.UomRepository;
import com.springmfg.ims.partner.repository.SupplierRepository;

@Service
public class MaterialService {

    private final MaterialRepository materialRepo;
    private final UomRepository uomRepo;
    private final SupplierRepository supplierRepo;
    private final AuditService auditService;

    public MaterialService(MaterialRepository materialRepo, UomRepository uomRepo,
                           SupplierRepository supplierRepo, AuditService auditService) {
        this.materialRepo = materialRepo;
        this.uomRepo = uomRepo;
        this.supplierRepo = supplierRepo;
        this.auditService = auditService;
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
        if (materialRepo.existsByMaterialCode(req.materialCode())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Material code already exists: " + req.materialCode());
        }
        validateUom(req.uom());
        Material m = new Material();
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
        m.setMaterialCode(req.materialCode());
        m.setName(req.name());
        m.setMaterialType(req.materialType());
        m.setGrade(req.grade());
        m.setDiameterMm(req.diameterMm());
        m.setUom(req.uom());
        m.setMinStock(req.minStock());
        m.setReorderLevel(req.reorderLevel());
        m.setMaxStock(req.maxStock());
        m.setStandardCost(req.standardCost());
        m.setShelfLifeDays(req.shelfLifeDays());
        m.setDescription(req.description());

        if (req.preferredSupplierId() != null) {
            m.setPreferredSupplier(supplierRepo.findById(req.preferredSupplierId())
                .orElseThrow(() -> new NotFoundException("Supplier not found: " + req.preferredSupplierId())));
        } else {
            m.setPreferredSupplier(null);
        }

        if (req.minStock().compareTo(req.reorderLevel()) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                "min_stock must be ≤ reorder_level");
        }
        if (req.maxStock() != null && req.reorderLevel().compareTo(req.maxStock()) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                "reorder_level must be ≤ max_stock");
        }
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
