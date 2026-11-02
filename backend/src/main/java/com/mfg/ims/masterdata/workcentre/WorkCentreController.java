package com.mfg.ims.masterdata.workcentre;

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

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/masterdata/work-centres")
@Tag(name = "Work Centres")
public class WorkCentreController {

    private final WorkCentreService service;

    public WorkCentreController(WorkCentreService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('WORK_CENTRE_VIEW','WORK_CENTRE_MANAGE')")
    public List<WorkCentreDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('WORK_CENTRE_VIEW','WORK_CENTRE_MANAGE')")
    public WorkCentreDto findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('WORK_CENTRE_MANAGE')")
    public WorkCentreDto create(@Valid @RequestBody SaveWorkCentreRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('WORK_CENTRE_MANAGE')")
    public WorkCentreDto update(@PathVariable Long id, @Valid @RequestBody SaveWorkCentreRequest req) {
        return service.update(id, req);
    }
}
