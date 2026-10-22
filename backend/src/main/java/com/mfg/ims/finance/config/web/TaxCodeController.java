package com.mfg.ims.finance.config.web;

import com.mfg.ims.finance.config.dto.TaxCodeDto;
import com.mfg.ims.finance.config.service.TaxCodeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config/tax-codes")
public class TaxCodeController {

    private final TaxCodeService taxCodeService;

    public TaxCodeController(TaxCodeService taxCodeService) {
        this.taxCodeService = taxCodeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW')")
    public List<TaxCodeDto> listActive() {
        return taxCodeService.findActive();
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('GL_VIEW')")
    public List<TaxCodeDto> listAll() {
        return taxCodeService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GL_VIEW')")
    public TaxCodeDto getById(@PathVariable Long id) {
        return taxCodeService.findById(id);
    }
}
