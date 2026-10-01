package com.springmfg.ims.masterdata;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.common.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Materials and units of measure. {@code GET /api/materials/{id}/stock} arrives with the inventory module (Phase 2). */
@RestController
@Validated
@Tag(name = "Materials")
@SecurityRequirement(name = "bearerAuth")
public class MaterialController {

    private final MaterialService service;

    MaterialController(MaterialService service) {
        this.service = service;
    }

    @GetMapping("/api/materials")
    @PreAuthorize("hasAnyAuthority('MATERIAL_VIEW', 'MASTERDATA_MANAGE')")
    @Operation(summary = "List materials (search q; filter active, materialType, supplierId, uom; page; sort)")
    public PageResponse<MaterialDtos.MaterialSummary> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) MaterialType materialType,
            @RequestParam(required = false) Long supplierId, @RequestParam(required = false) String uom, Pageable pageable) {
        return service.list(q, active, materialType, supplierId, uom, pageable);
    }

    @GetMapping("/api/materials/{id}")
    @PreAuthorize("hasAnyAuthority('MATERIAL_VIEW', 'MASTERDATA_MANAGE')")
    public MaterialDtos.MaterialResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/api/materials")
    @PreAuthorize("hasAuthority('MASTERDATA_MANAGE')")
    public ResponseEntity<MaterialDtos.MaterialResponse> create(@Valid @RequestBody MaterialDtos.CreateMaterialRequest request) {
        MaterialDtos.MaterialResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/materials/" + created.id())).body(created);
    }

    @PutMapping("/api/materials/{id}")
    @PreAuthorize("hasAuthority('MASTERDATA_MANAGE')")
    public MaterialDtos.MaterialResponse update(@PathVariable long id, @Valid @RequestBody MaterialDtos.UpdateMaterialRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/api/materials/{id}/activate")
    @PreAuthorize("hasAuthority('MASTERDATA_MANAGE')")
    public MaterialDtos.MaterialResponse activate(@PathVariable long id) {
        return service.activate(id);
    }

    @DeleteMapping("/api/materials/{id}")
    @PreAuthorize("hasAuthority('MASTERDATA_MANAGE')")
    @Operation(summary = "Deactivate the material (never deletes). 422 DEACTIVATION_BLOCKED if it is still in use, unless force=true")
    public ResponseEntity<Void> deactivate(@PathVariable long id, @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(required = false) @Size(max = 500) String reason) {
        service.deactivate(id, force, reason);
        return ResponseEntity.noContent().build();
    }

    /** Reference data any signed-in user may read; forms need it to offer units of measure. */
    @GetMapping("/api/uoms")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Units of measure")
    public List<MaterialDtos.UomResponse> units() {
        return service.units();
    }
}
