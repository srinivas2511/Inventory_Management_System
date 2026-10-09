package com.springmfg.ims.material.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.partner.dto.ApprovedItemDto;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.partner.domain.SupplierApprovedMaterial;
import com.springmfg.ims.partner.repository.SupplierApprovedMaterialRepository;
import com.springmfg.ims.partner.repository.SupplierRepository;

/**
 * MDM-020 / GC-2: the materials a supplier is approved to supply. Maintaining the list is always possible; whether a
 * purchase order is held to it is the system setting {@code purchasing.enforce_approved_suppliers} (off by default,
 * so existing purchasing is unchanged until a plant decides to switch it on).
 */
@Service
@Transactional
public class SupplierApprovedMaterialService {

    public static final String ENFORCE_SETTING = "purchasing.enforce_approved_suppliers";

    private final SupplierApprovedMaterialRepository repo;
    private final SupplierRepository supplierRepo;
    private final MaterialRepository materialRepo;
    private final SystemSettingService settings;
    private final AuditService auditService;

    public SupplierApprovedMaterialService(SupplierApprovedMaterialRepository repo, SupplierRepository supplierRepo,
                                           MaterialRepository materialRepo, SystemSettingService settings,
                                           AuditService auditService) {
        this.repo = repo;
        this.supplierRepo = supplierRepo;
        this.materialRepo = materialRepo;
        this.settings = settings;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ApprovedItemDto> list(Long supplierId) {
        requireSupplier(supplierId);
        return repo.findBySupplierId(supplierId).stream()
                .map(a -> materialRepo.findById(a.getMaterialId()).orElse(null))
                .filter(m -> m != null)
                .map(m -> new ApprovedItemDto(m.getId(), m.getMaterialCode(), m.getName()))
                .sorted(java.util.Comparator.comparing(ApprovedItemDto::code))
                .toList();
    }

    /** Replaces the supplier's list with exactly these materials. */
    public List<ApprovedItemDto> replace(Long supplierId, List<Long> materialIds) {
        requireSupplier(supplierId);
        Set<Long> wanted = new HashSet<>(materialIds);
        for (Long id : wanted) {
            Material m = materialRepo.findById(id).orElseThrow(() -> new NotFoundException("Material", id));
            if (!m.isActive()) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Material " + m.getMaterialCode() + " is inactive and cannot be approved");
            }
        }
        List<SupplierApprovedMaterial> current = repo.findBySupplierId(supplierId);
        Set<Long> before = current.stream().map(SupplierApprovedMaterial::getMaterialId).collect(Collectors.toSet());
        for (SupplierApprovedMaterial a : current) {
            if (!wanted.contains(a.getMaterialId())) {
                repo.delete(a);
            }
        }
        repo.flush();
        for (Long id : wanted) {
            if (!before.contains(id)) {
                repo.save(new SupplierApprovedMaterial(supplierId, id));
            }
        }
        auditService.record("SUPPLIER_APPROVED_MATERIALS_SET", "SUPPLIER", String.valueOf(supplierId),
                before.stream().sorted().toList().toString(), wanted.stream().sorted().toList().toString(), null);
        return list(supplierId);
    }

    /** Called when a purchase order line is entered: refuses a material the supplier is not approved for. */
    @Transactional(readOnly = true)
    public void assertMaySupply(Long supplierId, Long materialId) {
        if (!settings.getBoolean(ENFORCE_SETTING, false)) {
            return;
        }
        if (!repo.existsBySupplierIdAndMaterialId(supplierId, materialId)) {
            String code = materialRepo.findById(materialId).map(Material::getMaterialCode)
                    .orElse(String.valueOf(materialId));
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Material " + code + " is not on this supplier's approved materials list");
        }
    }

    private void requireSupplier(Long supplierId) {
        supplierRepo.findById(supplierId).orElseThrow(() -> new NotFoundException("Supplier", supplierId));
    }
}
