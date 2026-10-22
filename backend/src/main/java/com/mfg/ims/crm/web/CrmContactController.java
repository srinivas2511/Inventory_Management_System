package com.mfg.ims.crm.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import com.mfg.ims.crm.dto.ContactDto;
import com.mfg.ims.crm.dto.SaveContactRequest;
import com.mfg.ims.crm.service.CrmContactService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/crm/contacts")
public class CrmContactController {

    private final CrmContactService service;

    public CrmContactController(CrmContactService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public Page<ContactDto> list(@RequestParam(required = false) Long customerId,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(required = false) Boolean active,
                                 @PageableDefault(size = 50, sort = "firstName") Pageable pageable) {
        return service.search(customerId, q, active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public ContactDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CUSTOMER_ACTIVITY')")
    public ContactDto create(@Valid @RequestBody SaveContactRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_ACTIVITY')")
    public ContactDto update(@PathVariable Long id, @Valid @RequestBody SaveContactRequest req) {
        return service.update(id, req);
    }

    /** Contacts are deactivated, never deleted, because activities and leads reference them. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CUSTOMER_ACTIVITY')")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }
}
