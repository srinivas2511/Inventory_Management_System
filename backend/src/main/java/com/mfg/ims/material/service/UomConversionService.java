package com.mfg.ims.material.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.material.domain.UomConversion;
import com.mfg.ims.material.dto.SaveUomConversionRequest;
import com.mfg.ims.material.dto.UomConversionDto;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.material.repository.UomConversionRepository;
import com.mfg.ims.material.repository.UomRepository;

/**
 * MDM-003 / GC-1: alternative units of measure with conversion factors. Stock stays in the material's base UOM, so
 * a quantity entered in another unit is converted to base on the way in (goods receipt, material issue) and back out on
 * the way to a report. Quantities are NUMERIC(18,3), so conversions round HALF_UP to three decimals.
 */
@Service
@Transactional
public class UomConversionService {

    private static final int QTY_SCALE = 3;

    private final UomConversionRepository repo;
    private final MaterialRepository materialRepo;
    private final UomRepository uomRepo;
    private final AuditService auditService;

    public UomConversionService(UomConversionRepository repo, MaterialRepository materialRepo, UomRepository uomRepo,
                                AuditService auditService) {
        this.repo = repo;
        this.materialRepo = materialRepo;
        this.uomRepo = uomRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UomConversionDto> list(Long materialId) {
        Material m = requireMaterial(materialId);
        return repo.findByMaterialIdOrderByAltUom(materialId).stream().map(c -> toDto(c, m)).toList();
    }

    public UomConversionDto save(Long materialId, SaveUomConversionRequest req) {
        Material m = requireMaterial(materialId);
        String alt = req.altUom().trim().toUpperCase();
        if (alt.equals(m.getUom())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    alt + " is already the base unit of this material");
        }
        if (!uomRepo.existsById(alt)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Unknown unit of measure " + alt);
        }
        UomConversion c = repo.findByMaterialIdAndAltUom(materialId, alt).orElseGet(() -> {
            UomConversion n = new UomConversion();
            n.setMaterialId(materialId);
            n.setAltUom(alt);
            return n;
        });
        String old = c.getId() == null ? null : c.getFactorToBase().toPlainString();
        c.setFactorToBase(req.factorToBase());
        c = repo.save(c);
        auditService.record("UOM_CONVERSION_SAVED", "MATERIAL", String.valueOf(materialId), old,
                "1 " + alt + " = " + req.factorToBase().toPlainString() + " " + m.getUom(), null);
        return toDto(c, m);
    }

    public void delete(Long materialId, String altUom) {
        Material m = requireMaterial(materialId);
        UomConversion c = repo.findByMaterialIdAndAltUom(materialId, altUom.trim().toUpperCase())
                .orElseThrow(() -> new NotFoundException("UomConversion", altUom));
        repo.delete(c);
        auditService.record("UOM_CONVERSION_REMOVED", "MATERIAL", String.valueOf(materialId),
                "1 " + c.getAltUom() + " = " + c.getFactorToBase().toPlainString() + " " + m.getUom(), null, null);
    }

    /** A quantity entered in {@code uom} (null or the base unit means it already is base) expressed in the base unit. */
    @Transactional(readOnly = true)
    public BigDecimal toBase(Long materialId, BigDecimal qty, String uom) {
        if (uom == null || uom.isBlank() || qty == null) {
            return qty;
        }
        Material m = requireMaterial(materialId);
        String unit = uom.trim().toUpperCase();
        if (unit.equals(m.getUom())) {
            return qty;
        }
        UomConversion c = repo.findByMaterialIdAndAltUom(materialId, unit).orElseThrow(() ->
                new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "No conversion from " + unit + " to "
                        + m.getUom() + " is defined for material " + m.getMaterialCode()));
        return qty.multiply(c.getFactorToBase()).setScale(QTY_SCALE, RoundingMode.HALF_UP);
    }

    /** A base-unit quantity expressed in {@code uom} (for reports). */
    @Transactional(readOnly = true)
    public BigDecimal fromBase(Long materialId, BigDecimal baseQty, String uom) {
        if (uom == null || uom.isBlank() || baseQty == null) {
            return baseQty;
        }
        Material m = requireMaterial(materialId);
        String unit = uom.trim().toUpperCase();
        if (unit.equals(m.getUom())) {
            return baseQty;
        }
        UomConversion c = repo.findByMaterialIdAndAltUom(materialId, unit).orElseThrow(() ->
                new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "No conversion from " + m.getUom() + " to "
                        + unit + " is defined for material " + m.getMaterialCode()));
        return baseQty.divide(c.getFactorToBase(), QTY_SCALE, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public String baseUom(Long materialId) {
        return requireMaterial(materialId).getUom();
    }

    private Material requireMaterial(Long id) {
        return materialRepo.findById(id).orElseThrow(() -> new NotFoundException("Material", id));
    }

    private static UomConversionDto toDto(UomConversion c, Material m) {
        return new UomConversionDto(c.getId(), c.getMaterialId(), m.getUom(), c.getAltUom(), c.getFactorToBase());
    }
}
