package com.springmfg.ims.crm.web;

import java.util.List;

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

import com.springmfg.ims.crm.dto.CrmCustomerDto;
import com.springmfg.ims.crm.dto.SaveTagRequest;
import com.springmfg.ims.crm.dto.SetCustomerTagsRequest;
import com.springmfg.ims.crm.dto.TagDto;
import com.springmfg.ims.crm.service.CustomerTagService;

import jakarta.validation.Valid;

/** Customer segmentation: the tag master and the customer list that can be filtered by tag. */
@RestController
@RequestMapping("/api/crm")
public class CustomerTagController {

    private final CustomerTagService service;

    public CustomerTagController(CustomerTagService service) {
        this.service = service;
    }

    @GetMapping("/tags")
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public List<TagDto> listTags() {
        return service.listTags();
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CUSTOMER_TAG_MANAGE')")
    public TagDto createTag(@Valid @RequestBody SaveTagRequest req) {
        return service.createTag(req);
    }

    @PutMapping("/tags/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_TAG_MANAGE')")
    public TagDto updateTag(@PathVariable Long id, @Valid @RequestBody SaveTagRequest req) {
        return service.updateTag(id, req);
    }

    @DeleteMapping("/tags/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CUSTOMER_TAG_MANAGE')")
    public void deleteTag(@PathVariable Long id) {
        service.deleteTag(id);
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public Page<CrmCustomerDto> customers(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) Long tagId,
                                          @PageableDefault(size = 50, sort = "name") Pageable pageable) {
        return service.searchCustomers(q, tagId, pageable);
    }

    @GetMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public CrmCustomerDto customer(@PathVariable Long id) {
        return service.getCustomer(id);
    }

    @PutMapping("/customers/{id}/tags")
    @PreAuthorize("hasAuthority('CUSTOMER_TAG_MANAGE')")
    public CrmCustomerDto setTags(@PathVariable Long id, @Valid @RequestBody SetCustomerTagsRequest req) {
        return service.setCustomerTags(id, req.tagIds());
    }
}
