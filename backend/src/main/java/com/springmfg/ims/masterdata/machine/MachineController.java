package com.springmfg.ims.masterdata.machine;

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
@RequestMapping("/api/machines")
public class MachineController {

    private final MachineService service;

    public MachineController(MachineService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MASTERDATA_VIEW')")
    public List<MachineDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return activeOnly ? service.findActive() : service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTERDATA_VIEW')")
    public MachineDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MACHINE_MANAGE')")
    public MachineDto save(@Valid @RequestBody SaveMachineRequest req) {
        return service.save(req);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('MACHINE_MANAGE')")
    public MachineDto updateStatus(@PathVariable Long id, @RequestBody String status) {
        return service.updateStatus(id, status.trim().replace("\"", ""));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('MACHINE_MANAGE')")
    public MachineDto deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }
}
