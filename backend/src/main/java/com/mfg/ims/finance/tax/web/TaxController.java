package com.mfg.ims.finance.tax.web;

import com.mfg.ims.finance.tax.dto.TaxDtos.DeterminationDto;
import com.mfg.ims.finance.tax.dto.TaxDtos.ProductSlabDto;
import com.mfg.ims.finance.tax.dto.TaxDtos.ProductSlabRequest;
import com.mfg.ims.finance.tax.dto.TaxDtos.SlabDto;
import com.mfg.ims.finance.tax.service.TaxAdminService;
import com.mfg.ims.finance.tax.service.TaxDeterminationService.Direction;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/finance/tax")
public class TaxController {

    private final TaxAdminService service;

    public TaxController(TaxAdminService service) {
        this.service = service;
    }

    @GetMapping("/slabs")
    @PreAuthorize("hasAnyAuthority('GL_VIEW','AP_MANAGE','AR_MANAGE','TAX_MANAGE')")
    public List<SlabDto> slabs() {
        return service.listSlabs();
    }

    /** What a document of this amount would be taxed: the supply type, the components and the total. */
    @GetMapping("/determine")
    @PreAuthorize("hasAnyAuthority('GL_VIEW','AP_MANAGE','AR_MANAGE','TAX_MANAGE')")
    public DeterminationDto determine(@RequestParam Direction direction,
                                      @RequestParam String slab,
                                      @RequestParam(required = false) Long partnerId,
                                      @RequestParam BigDecimal amount) {
        return service.preview(direction, slab, partnerId, amount);
    }

    @GetMapping("/product-slabs/{productId}")
    @PreAuthorize("hasAnyAuthority('GL_VIEW','AR_MANAGE','TAX_MANAGE')")
    public ProductSlabDto productSlab(@PathVariable Long productId) {
        return service.productSlab(productId);
    }

    @PutMapping("/product-slabs/{productId}")
    @PreAuthorize("hasAuthority('TAX_MANAGE')")
    public ProductSlabDto assign(@PathVariable Long productId, @Valid @RequestBody ProductSlabRequest request) {
        return service.assignProductSlab(productId, request.slabCode());
    }
}
