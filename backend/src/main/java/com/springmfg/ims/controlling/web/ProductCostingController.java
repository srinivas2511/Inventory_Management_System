package com.springmfg.ims.controlling.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.controlling.dto.CostEstimateDto;
import com.springmfg.ims.controlling.dto.CreateCostEstimateRequest;
import com.springmfg.ims.controlling.service.ProductCostingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/controlling/cost-estimates")
public class ProductCostingController {

    private final ProductCostingService service;

    public ProductCostingController(ProductCostingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONTROLLING_VIEW')")
    public Page<CostEstimateDto> search(@RequestParam(required = false) Long productId,
                                        @RequestParam(required = false) String status,
                                        @PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC)
                                        Pageable pageable) {
        return service.search(productId, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTROLLING_VIEW')")
    public CostEstimateDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONTROLLING_MANAGE')")
    public CostEstimateDto estimate(@Valid @RequestBody CreateCostEstimateRequest req) {
        return service.estimate(req.productId(), req.bomId(), req.costCentreId());
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority('COST_ESTIMATE_RELEASE')")
    public CostEstimateDto release(@PathVariable Long id) {
        return service.release(id);
    }
}
