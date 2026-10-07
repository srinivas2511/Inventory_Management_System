package com.springmfg.ims.production.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.production.dto.AssignOperatorRequest;
import com.springmfg.ims.production.dto.AssignableOperatorDto;
import com.springmfg.ims.production.dto.ProductionOperationDto;
import com.springmfg.ims.production.dto.RecordOutputRequest;
import com.springmfg.ims.production.service.OperationAssignmentService;
import com.springmfg.ims.production.service.OperationExecutionService;
import com.springmfg.ims.production.service.OperatorDirectory;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/production")
public class ProductionOperationController {

    private final OperationExecutionService service;
    private final OperationAssignmentService assignmentService;
    private final OperatorDirectory operatorDirectory;

    public ProductionOperationController(OperationExecutionService service,
                                         OperationAssignmentService assignmentService,
                                         OperatorDirectory operatorDirectory) {
        this.service = service;
        this.assignmentService = assignmentService;
        this.operatorDirectory = operatorDirectory;
    }

    @PostMapping("/operations/{id}/start")
    @PreAuthorize("hasAuthority('PRODUCTION_EXECUTE')")
    public ProductionOperationDto start(@PathVariable Long id,
                                        @RequestParam(required = false) Long machineId) {
        return service.startOperation(id, machineId);
    }

    @PostMapping("/operations/{id}/pause")
    @PreAuthorize("hasAuthority('PRODUCTION_EXECUTE')")
    public ProductionOperationDto pause(@PathVariable Long id) {
        return service.pauseOperation(id);
    }

    @PostMapping("/operations/{id}/resume")
    @PreAuthorize("hasAuthority('PRODUCTION_EXECUTE')")
    public ProductionOperationDto resume(@PathVariable Long id) {
        return service.resumeOperation(id);
    }

    @PostMapping("/operations/{id}/complete")
    @PreAuthorize("hasAuthority('PRODUCTION_EXECUTE')")
    public ProductionOperationDto complete(@PathVariable Long id) {
        return service.completeOperation(id);
    }

    @PostMapping("/outputs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCTION_EXECUTE')")
    public ProductionOperationDto recordOutput(@Valid @RequestBody RecordOutputRequest req) {
        return service.recordOutput(req);
    }

    @GetMapping("/my-work")
    @PreAuthorize("hasAuthority('PRODUCTION_EXECUTE')")
    public List<ProductionOperationDto> myWork() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = auth != null && auth.getPrincipal() instanceof CachedUser u ? u.id() : null;
        if (userId == null) return List.of();
        return service.getMyWork(userId);
    }

    @PutMapping("/operations/{id}/assignment")
    @PreAuthorize("hasAuthority('PRODUCTION_ASSIGN')")
    public ProductionOperationDto assign(@PathVariable Long id, @Valid @RequestBody AssignOperatorRequest req) {
        return assignmentService.assign(id, req.operatorId(), req.reason());
    }

    @GetMapping("/assignable-operators")
    @PreAuthorize("hasAuthority('PRODUCTION_ASSIGN')")
    public List<AssignableOperatorDto> assignableOperators(@RequestParam(required = false) String q) {
        return operatorDirectory.assignable(q);
    }
}
