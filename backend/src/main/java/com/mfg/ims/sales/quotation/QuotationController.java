package com.mfg.ims.sales.quotation;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales/quotations")
public class QuotationController {

    private final QuotationService service;

    public QuotationController(QuotationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('QUOTATION_VIEW')")
    public List<QuotationDto> list() {
        return service.list(1L);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('QUOTATION_VIEW')")
    public QuotationDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('QUOTATION_CREATE')")
    public QuotationDto create(@Valid @RequestBody SaveQuotationRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('QUOTATION_UPDATE')")
    public QuotationDto update(@PathVariable Long id, @Valid @RequestBody SaveQuotationRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('QUOTATION_UPDATE')")
    public QuotationDto submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('QUOTATION_CANCEL')")
    public QuotationDto cancel(@PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return service.cancel(id, reason != null ? reason : "Cancelled");
    }

    @PostMapping("/{id}/lost")
    @PreAuthorize("hasAuthority('QUOTATION_UPDATE')")
    public QuotationDto markLost(@PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return service.markLost(id, reason != null ? reason : "Lost");
    }
}
