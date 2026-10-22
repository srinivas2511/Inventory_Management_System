package com.mfg.ims.controlling.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.controlling.dto.ProfitCentreDto;
import com.mfg.ims.controlling.dto.SaveProfitCentreRequest;
import com.mfg.ims.controlling.service.ProfitCentreService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/controlling/profit-centres")
public class ProfitCentreController {

    private final ProfitCentreService service;

    public ProfitCentreController(ProfitCentreService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONTROLLING_VIEW')")
    public List<ProfitCentreDto> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTROLLING_VIEW')")
    public ProfitCentreDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONTROLLING_MANAGE')")
    public ProfitCentreDto create(@Valid @RequestBody SaveProfitCentreRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTROLLING_MANAGE')")
    public ProfitCentreDto update(@PathVariable Long id, @Valid @RequestBody SaveProfitCentreRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('CONTROLLING_MANAGE')")
    public ProfitCentreDto activate(@PathVariable Long id) {
        return service.setActive(id, true);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('CONTROLLING_MANAGE')")
    public ProfitCentreDto deactivate(@PathVariable Long id) {
        return service.setActive(id, false);
    }
}
