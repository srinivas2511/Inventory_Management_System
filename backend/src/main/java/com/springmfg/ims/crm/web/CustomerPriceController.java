package com.springmfg.ims.crm.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.crm.dto.PriceDto;
import com.springmfg.ims.crm.dto.ResolvedPriceDto;
import com.springmfg.ims.crm.dto.SavePriceRequest;
import com.springmfg.ims.crm.service.CustomerPriceService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/crm/prices")
public class CustomerPriceController {

    private final CustomerPriceService service;

    public CustomerPriceController(CustomerPriceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public Page<PriceDto> list(@RequestParam(required = false) Long customerId,
                               @RequestParam(required = false) Long productId,
                               @RequestParam(required = false) Boolean active,
                               @PageableDefault(size = 50, sort = "productId") Pageable pageable) {
        return service.search(customerId, productId, active, pageable);
    }

    /** The price a customer would be quoted for a product and quantity; used to pre-fill quotation lines. */
    @GetMapping("/resolve")
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public ResolvedPriceDto resolve(@RequestParam Long customerId, @RequestParam Long productId,
                                    @RequestParam(defaultValue = "1") BigDecimal quantity,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate) {
        return service.resolveDto(customerId, productId, quantity, onDate != null ? onDate : LocalDate.now());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRICE_LIST_MANAGE')")
    public PriceDto create(@Valid @RequestBody SavePriceRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRICE_LIST_MANAGE')")
    public PriceDto update(@PathVariable Long id, @Valid @RequestBody SavePriceRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PRICE_LIST_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
