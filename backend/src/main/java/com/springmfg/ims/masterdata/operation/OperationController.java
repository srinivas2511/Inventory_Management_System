package com.springmfg.ims.masterdata.operation;

import java.util.List;

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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/operations")
public class OperationController {

    private final OperationService service;

    public OperationController(OperationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('OPERATION_MANAGE','MASTERDATA_MANAGE','PRODUCTION_VIEW','ROUTING_MANAGE')")
    public List<OperationDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return activeOnly ? service.findActive() : service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('OPERATION_MANAGE','MASTERDATA_MANAGE','PRODUCTION_VIEW','ROUTING_MANAGE')")
    public OperationDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTERDATA_MANAGE')")
    public OperationDto save(@Valid @RequestBody SaveOperationRequest req) {
        return service.save(req);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('MASTERDATA_MANAGE')")
    public OperationDto deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }
}
