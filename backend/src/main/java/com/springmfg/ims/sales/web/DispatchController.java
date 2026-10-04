package com.springmfg.ims.sales.web;

import java.time.LocalDate;

import jakarta.validation.Valid;

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

import com.springmfg.ims.sales.dto.CreateDispatchRequest;
import com.springmfg.ims.sales.dto.DispatchDto;
import com.springmfg.ims.sales.dto.DispatchNoteDto;
import com.springmfg.ims.sales.service.DispatchService;

@RestController
@RequestMapping("/api/sales/dispatches")
public class DispatchController {

    private final DispatchService service;

    public DispatchController(DispatchService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DISPATCH_CREATE')")
    public DispatchDto create(@Valid @RequestBody CreateDispatchRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('DISPATCH_VIEW','DISPATCH_CREATE','SALES_VIEW')")
    public DispatchDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('DISPATCH_VIEW','DISPATCH_CREATE','SALES_VIEW')")
    public Page<DispatchDto> search(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(customerId, from, to, pageable);
    }

    @GetMapping("/{id}/dispatch-note")
    @PreAuthorize("hasAnyAuthority('DISPATCH_VIEW','DISPATCH_CREATE')")
    public DispatchNoteDto dispatchNote(@PathVariable Long id) {
        return service.generateDispatchNote(id);
    }
}
