package com.springmfg.ims.maintenance.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import com.springmfg.ims.maintenance.dto.CreateMaintenanceRequestDto;
import com.springmfg.ims.maintenance.dto.CreatePmScheduleDto;
import com.springmfg.ims.maintenance.dto.MaintenanceRequestDto;
import com.springmfg.ims.maintenance.dto.PmScheduleDto;
import com.springmfg.ims.maintenance.service.MaintenanceService;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceService service;

    public MaintenanceController(MaintenanceService service) {
        this.service = service;
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAuthority('MAINTENANCE_VIEW')")
    public Page<MaintenanceRequestDto> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestType,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.list(status, requestType, pageable);
    }

    @GetMapping("/requests/{id}")
    @PreAuthorize("hasAuthority('MAINTENANCE_VIEW')")
    public MaintenanceRequestDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public MaintenanceRequestDto create(@Valid @RequestBody CreateMaintenanceRequestDto dto) {
        return service.create(dto);
    }

    @PutMapping("/requests/{id}/status")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public MaintenanceRequestDto updateStatus(
            @PathVariable Long id,
            @RequestParam String newStatus,
            @RequestParam(required = false) String resolutionNotes) {
        return service.updateStatus(id, newStatus, resolutionNotes);
    }

    @GetMapping("/pm-schedules")
    @PreAuthorize("hasAuthority('MAINTENANCE_VIEW')")
    public List<PmScheduleDto> listPmSchedules() {
        return service.listPmSchedules();
    }

    @PostMapping("/pm-schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public PmScheduleDto createPmSchedule(@Valid @RequestBody CreatePmScheduleDto dto) {
        return service.createPmSchedule(dto.machineId(), dto.taskName(), dto.frequencyDays(), dto.assignedTo());
    }

    @PostMapping("/pm-schedules/{id}/generate-request")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public MaintenanceRequestDto generatePmRequest(@PathVariable Long id) {
        return service.generatePmRequest(id);
    }
}
