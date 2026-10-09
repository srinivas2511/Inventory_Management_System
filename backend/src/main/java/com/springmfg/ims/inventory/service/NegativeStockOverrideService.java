package com.springmfg.ims.inventory.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.inventory.dto.NegativeStockOverrideDto;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;

/**
 * INV-004 / GC-3: stock cannot go negative unless an administrator enables it, and the permission is configurable per
 * item, beside the global setting {@code inventory.allow_negative_stock}. Every change needs a reason and is audited
 * with the old and new value in the same transaction. The flag is deliberately NOT part of the ordinary material and
 * product save requests, so a master-data editor cannot switch it.
 */
@Service
@Transactional
public class NegativeStockOverrideService {

    private final MaterialRepository materialRepo;
    private final ProductRepository productRepo;
    private final AuditService auditService;

    public NegativeStockOverrideService(MaterialRepository materialRepo, ProductRepository productRepo,
                                        AuditService auditService) {
        this.materialRepo = materialRepo;
        this.productRepo = productRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<NegativeStockOverrideDto> list() {
        List<NegativeStockOverrideDto> out = new ArrayList<>();
        for (Material m : materialRepo.findByAllowNegativeStockTrueOrderByMaterialCode()) {
            out.add(toDto(m));
        }
        for (Product p : productRepo.findByAllowNegativeStockTrueOrderByProductCode()) {
            out.add(toDto(p));
        }
        return out;
    }

    public NegativeStockOverrideDto setForMaterial(Long id, boolean allowed, String reason) {
        Material m = materialRepo.findById(id).orElseThrow(() -> new NotFoundException("Material", id));
        boolean old = m.isAllowNegativeStock();
        m.setAllowNegativeStock(allowed);
        materialRepo.save(m);
        auditService.record("NEGATIVE_STOCK_OVERRIDE", "MATERIAL", String.valueOf(id),
                String.valueOf(old), String.valueOf(allowed), reason);
        return toDto(m);
    }

    public NegativeStockOverrideDto setForProduct(Long id, boolean allowed, String reason) {
        Product p = productRepo.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
        boolean old = p.isAllowNegativeStock();
        p.setAllowNegativeStock(allowed);
        productRepo.save(p);
        auditService.record("NEGATIVE_STOCK_OVERRIDE", "PRODUCT", String.valueOf(id),
                String.valueOf(old), String.valueOf(allowed), reason);
        return toDto(p);
    }

    private static NegativeStockOverrideDto toDto(Material m) {
        return new NegativeStockOverrideDto("MATERIAL", m.getId(), m.getMaterialCode(), m.getName(),
                m.isAllowNegativeStock());
    }

    private static NegativeStockOverrideDto toDto(Product p) {
        return new NegativeStockOverrideDto("PRODUCT", p.getId(), p.getProductCode(), p.getName(),
                p.isAllowNegativeStock());
    }
}
