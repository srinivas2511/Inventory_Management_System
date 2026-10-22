package com.mfg.ims.material.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.material.dto.PurchasingInfoDto;
import com.mfg.ims.material.dto.SavePurchasingInfoRequest;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.material.repository.UomConversionRepository;
import com.mfg.ims.material.repository.UomRepository;
import com.mfg.ims.partner.domain.Supplier;
import com.mfg.ims.partner.repository.SupplierRepository;

/**
 * MDM-004 / GC-2: purchasing information of a material - the preferred supplier (which already existed on the
 * material), the unit it is purchased in and the tax code purchases default to. The purchasing unit must be the base
 * unit or one that has a conversion, so a purchase quantity can always be turned into stock.
 */
@Service
@Transactional
public class MaterialPurchasingInfoService {

    private final MaterialRepository materialRepo;
    private final SupplierRepository supplierRepo;
    private final UomRepository uomRepo;
    private final UomConversionRepository conversionRepo;
    private final AuditService auditService;

    public MaterialPurchasingInfoService(MaterialRepository materialRepo, SupplierRepository supplierRepo,
                                         UomRepository uomRepo, UomConversionRepository conversionRepo,
                                         AuditService auditService) {
        this.materialRepo = materialRepo;
        this.supplierRepo = supplierRepo;
        this.uomRepo = uomRepo;
        this.conversionRepo = conversionRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PurchasingInfoDto get(Long materialId) {
        return toDto(require(materialId));
    }

    public PurchasingInfoDto save(Long materialId, SavePurchasingInfoRequest req) {
        Material m = require(materialId);
        Supplier supplier = null;
        if (req.preferredSupplierId() != null) {
            supplier = supplierRepo.findById(req.preferredSupplierId())
                    .orElseThrow(() -> new NotFoundException("Supplier", req.preferredSupplierId()));
        }
        String uom = blankToNull(req.purchaseUom());
        if (uom != null) {
            uom = uom.toUpperCase();
            if (!uomRepo.existsById(uom)) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Unknown unit of measure " + uom);
            }
            if (!uom.equals(m.getUom()) && conversionRepo.findByMaterialIdAndAltUom(materialId, uom).isEmpty()) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Define the conversion from " + uom
                        + " to " + m.getUom() + " before buying this material in " + uom);
            }
        }
        String old = describe(m);
        m.setPreferredSupplier(supplier);
        m.setPurchaseUom(uom);
        m.setPurchaseTaxCode(blankToNull(req.purchaseTaxCode()));
        materialRepo.save(m);
        auditService.record("MATERIAL_PURCHASING_INFO_SAVED", "MATERIAL", String.valueOf(materialId), old,
                describe(m), null);
        return toDto(m);
    }

    private Material require(Long id) {
        return materialRepo.findById(id).orElseThrow(() -> new NotFoundException("Material", id));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String describe(Material m) {
        return "supplier=" + (m.getPreferredSupplier() == null ? "-" : m.getPreferredSupplier().getId())
                + ", uom=" + m.getPurchaseUom() + ", tax=" + m.getPurchaseTaxCode();
    }

    private static PurchasingInfoDto toDto(Material m) {
        Supplier s = m.getPreferredSupplier();
        return new PurchasingInfoDto(m.getId(), m.getUom(), s == null ? null : s.getId(),
                s == null ? null : s.getName(), m.getPurchaseUom(), m.getPurchaseTaxCode());
    }
}
