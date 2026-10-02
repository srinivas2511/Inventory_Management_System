package com.springmfg.ims.material.web;

import java.util.List;

import jakarta.validation.Valid;

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

import com.springmfg.ims.material.dto.MaterialDto;
import com.springmfg.ims.material.dto.SaveMaterialRequest;
import com.springmfg.ims.material.dto.UomDto;
import com.springmfg.ims.material.service.MaterialService;

@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService service;

    public MaterialController(MaterialService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MASTER_MATERIAL_VIEW','MASTER_MATERIAL_MANAGE')")
    public Page<MaterialDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(q, type, active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('MASTER_MATERIAL_VIEW','MASTER_MATERIAL_MANAGE')")
    public MaterialDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_MATERIAL_MANAGE')")
    public MaterialDto create(@Valid @RequestBody SaveMaterialRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_MATERIAL_MANAGE')")
    public MaterialDto update(@PathVariable Long id, @Valid @RequestBody SaveMaterialRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('MASTER_MATERIAL_MANAGE')")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }

    @GetMapping("/uoms")
    @PreAuthorize("isAuthenticated()")
    public List<UomDto> uoms() {
        return service.listUoms();
    }
}
