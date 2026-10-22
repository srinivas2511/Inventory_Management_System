package com.mfg.ims.plm.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.plm.domain.ProductLifecycleStatus;
import com.mfg.ims.plm.dto.LifecycleDtos.ProductLifecycleDto;
import com.mfg.ims.plm.dto.LifecycleDtos.ProductLifecycleRowDto;
import com.mfg.ims.plm.dto.LifecycleDtos.TransitionRequest;
import com.mfg.ims.plm.service.ProductLifecycleService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/product-lifecycle")
public class ProductLifecycleController {

    private final ProductLifecycleService service;

    public ProductLifecycleController(ProductLifecycleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PLM_VIEW')")
    public Page<ProductLifecycleRowDto> list(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) ProductLifecycleStatus status,
                                             @PageableDefault(size = 50, sort = "productCode", direction = Sort.Direction.ASC)
                                             Pageable pageable) {
        return service.list(q, status, pageable);
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAuthority('PLM_VIEW')")
    public ProductLifecycleDto get(@PathVariable Long productId) {
        return service.get(productId);
    }

    @PostMapping("/{productId}/transition")
    @PreAuthorize("hasAuthority('LIFECYCLE_MANAGE')")
    public ProductLifecycleDto transition(@PathVariable Long productId, @Valid @RequestBody TransitionRequest req) {
        return service.transition(productId, req);
    }
}
