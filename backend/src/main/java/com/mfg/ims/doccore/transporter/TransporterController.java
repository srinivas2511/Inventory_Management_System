package com.mfg.ims.doccore.transporter;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/transporters")
public class TransporterController {

    private final TransporterService service;

    public TransporterController(TransporterService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TRANSPORTER_MANAGE','SETTINGS_MANAGE')")
    public List<TransporterDto> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TRANSPORTER_MANAGE')")
    public TransporterDto create(@Valid @RequestBody SaveTransporterRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSPORTER_MANAGE')")
    public TransporterDto update(@PathVariable Long id, @Valid @RequestBody SaveTransporterRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TRANSPORTER_MANAGE')")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }
}
