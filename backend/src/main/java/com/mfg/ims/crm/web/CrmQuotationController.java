package com.mfg.ims.crm.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

import com.mfg.ims.crm.dto.QuotationDto;
import com.mfg.ims.crm.dto.RejectQuotationRequest;
import com.mfg.ims.crm.dto.SaveQuotationRequest;
import com.mfg.ims.crm.service.CrmQuotationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/crm/quotations")
public class CrmQuotationController {

    private final CrmQuotationService service;

    public CrmQuotationController(CrmQuotationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public Page<QuotationDto> list(@RequestParam(required = false) Long customerId,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) Long opportunityId,
                                   @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(customerId, status, opportunityId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public QuotationDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('QUOTATION_CREATE')")
    public QuotationDto create(@Valid @RequestBody SaveQuotationRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('QUOTATION_CREATE')")
    public QuotationDto update(@PathVariable Long id, @Valid @RequestBody SaveQuotationRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('QUOTATION_CREATE')")
    public QuotationDto send(@PathVariable Long id) {
        return service.send(id);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('QUOTATION_CREATE')")
    public QuotationDto accept(@PathVariable Long id) {
        return service.accept(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('QUOTATION_CREATE')")
    public QuotationDto reject(@PathVariable Long id, @Valid @RequestBody RejectQuotationRequest req) {
        return service.reject(id, req.reason());
    }

    @PostMapping("/{id}/convert-to-sales-order")
    @PreAuthorize("hasAuthority('QUOTATION_CREATE')")
    public QuotationDto convertToSalesOrder(@PathVariable Long id) {
        return service.convertToSalesOrder(id);
    }
}
