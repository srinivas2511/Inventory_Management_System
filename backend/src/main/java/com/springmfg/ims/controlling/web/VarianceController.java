package com.springmfg.ims.controlling.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.controlling.dto.VarianceDto;
import com.springmfg.ims.controlling.service.ProductionOrderVarianceService;

@RestController
@RequestMapping("/api/controlling/variances")
public class VarianceController {

    private final ProductionOrderVarianceService service;

    public VarianceController(ProductionOrderVarianceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONTROLLING_VIEW')")
    public Page<VarianceDto> list(@PageableDefault(size = 20, sort = "calculatedAt", direction = Sort.Direction.DESC)
                                  Pageable pageable) {
        return service.search(pageable);
    }

    @GetMapping("/{productionOrderId}")
    @PreAuthorize("hasAuthority('CONTROLLING_VIEW')")
    public VarianceDto get(@PathVariable Long productionOrderId) {
        return service.findByOrder(productionOrderId);
    }

    @PostMapping("/{productionOrderId}/calculate")
    @PreAuthorize("hasAuthority('CONTROLLING_MANAGE')")
    public VarianceDto calculate(@PathVariable Long productionOrderId) {
        return service.calculate(productionOrderId);
    }
}
