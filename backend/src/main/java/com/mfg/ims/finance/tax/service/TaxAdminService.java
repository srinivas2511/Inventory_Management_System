package com.mfg.ims.finance.tax.service;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.finance.config.domain.TaxCode;
import com.mfg.ims.finance.config.repository.TaxCodeRepository;
import com.mfg.ims.finance.tax.domain.ProductTaxSlab;
import com.mfg.ims.finance.tax.dto.TaxDtos.DeterminationDto;
import com.mfg.ims.finance.tax.dto.TaxDtos.ProductSlabDto;
import com.mfg.ims.finance.tax.dto.TaxDtos.SlabComponentDto;
import com.mfg.ims.finance.tax.dto.TaxDtos.SlabDto;
import com.mfg.ims.finance.tax.repository.ProductTaxSlabRepository;
import com.mfg.ims.finance.tax.repository.TaxSlabRepository;
import com.mfg.ims.finance.tax.service.TaxDeterminationService.Direction;
import com.mfg.ims.partner.repository.CustomerRepository;
import com.mfg.ims.partner.repository.SupplierRepository;
import com.mfg.ims.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads the tax determination data and maintains the slab a product is sold under. */
@Service
@Transactional
public class TaxAdminService {

    private final TaxSlabRepository slabs;
    private final TaxCodeRepository taxCodes;
    private final ProductTaxSlabRepository productSlabs;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final CustomerRepository customers;
    private final TaxDeterminationService determination;
    private final AuditService audit;

    public TaxAdminService(TaxSlabRepository slabs, TaxCodeRepository taxCodes, ProductTaxSlabRepository productSlabs,
                           ProductRepository products, SupplierRepository suppliers, CustomerRepository customers,
                           TaxDeterminationService determination, AuditService audit) {
        this.slabs = slabs;
        this.taxCodes = taxCodes;
        this.productSlabs = productSlabs;
        this.products = products;
        this.suppliers = suppliers;
        this.customers = customers;
        this.determination = determination;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SlabDto> listSlabs() {
        var rows = slabs.findByActiveTrueOrderBySlabCodeAscSupplyTypeAscIdAsc();
        Map<Long, TaxCode> codes = taxCodes.findAllById(rows.stream().map(r -> r.getTaxCodeId()).toList()).stream()
                .collect(Collectors.toMap(TaxCode::getId, c -> c));
        Map<String, String> descriptions = new LinkedHashMap<>();
        Map<String, List<SlabComponentDto>> components = new LinkedHashMap<>();
        for (var row : rows) {
            TaxCode tc = codes.get(row.getTaxCodeId());
            descriptions.putIfAbsent(row.getSlabCode(), row.getDescription());
            components.computeIfAbsent(row.getSlabCode(), k -> new ArrayList<>())
                    .add(new SlabComponentDto(row.getSupplyType(), tc.getTaxCode(), tc.getTaxType(), tc.getRatePct()));
        }
        return descriptions.entrySet().stream()
                .map(e -> new SlabDto(e.getKey(), e.getValue(), components.get(e.getKey()))).toList();
    }

    @Transactional(readOnly = true)
    public DeterminationDto preview(Direction direction, String slabCode, Long partnerId, BigDecimal amount) {
        String gst = null;
        if (partnerId != null) {
            gst = direction == Direction.PURCHASE
                    ? suppliers.findById(partnerId).orElseThrow(() -> new NotFoundException("Supplier", partnerId)).getGstNumber()
                    : customers.findById(partnerId).orElseThrow(() -> new NotFoundException("Customer", partnerId)).getGstNumber();
        }
        return determination.determine(direction, slabCode, gst, amount).toDto();
    }

    @Transactional(readOnly = true)
    public ProductSlabDto productSlab(Long productId) {
        return new ProductSlabDto(productId, productSlabs.findByProductId(productId).map(ProductTaxSlab::getSlabCode).orElse(null));
    }

    public ProductSlabDto assignProductSlab(Long productId, String slabCode) {
        products.findById(productId).orElseThrow(() -> new NotFoundException("Product", productId));
        String wanted = slabCode == null || slabCode.isBlank() ? null : slabCode.trim();
        if (wanted != null && !slabs.existsBySlabCodeAndActiveTrue(wanted)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Unknown tax slab " + wanted);
        }
        var existing = productSlabs.findByProductId(productId);
        String before = existing.map(ProductTaxSlab::getSlabCode).orElse(null);
        if (wanted == null) {
            existing.ifPresent(productSlabs::delete);
        } else {
            ProductTaxSlab row = existing.orElseGet(ProductTaxSlab::new);
            row.setProductId(productId);
            row.setSlabCode(wanted);
            productSlabs.save(row);
        }
        audit.record("PRODUCT_TAX_SLAB_CHANGED", "PRODUCT", String.valueOf(productId), before, wanted, null);
        return new ProductSlabDto(productId, wanted);
    }
}
