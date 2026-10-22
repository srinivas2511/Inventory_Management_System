package com.mfg.ims.mrp.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.mrp.dto.PlanningParamDto;
import com.mfg.ims.mrp.dto.SavePlanningParamRequest;
import com.mfg.ims.mrp.service.PlanningParamService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mrp/planning-params")
public class PlanningParamController {

    private final PlanningParamService service;

    public PlanningParamController(PlanningParamService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MRP_VIEW')")
    public List<PlanningParamDto> list() {
        return service.findAll();
    }

    /** Upsert keyed by the item (materialId or productId). */
    @PutMapping
    @PreAuthorize("hasAuthority('DEMAND_PLAN')")
    public PlanningParamDto save(@Valid @RequestBody SavePlanningParamRequest req) {
        return service.save(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DEMAND_PLAN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
