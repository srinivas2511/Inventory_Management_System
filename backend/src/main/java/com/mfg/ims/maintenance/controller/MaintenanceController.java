package com.mfg.ims.maintenance.controller;

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

import com.mfg.ims.maintenance.dto.CreateMaintenanceRequestDto;
import com.mfg.ims.maintenance.dto.CreatePmScheduleDto;
import com.mfg.ims.maintenance.dto.MaintenanceRequestDto;
import com.mfg.ims.maintenance.dto.MaintenanceWorkDtos.ActivityDto;
import com.mfg.ims.maintenance.dto.MaintenanceWorkDtos.MaintenanceCostDto;
import com.mfg.ims.maintenance.dto.MaintenanceWorkDtos.RecordActivityRequest;
import com.mfg.ims.maintenance.dto.PmScheduleDto;
import com.mfg.ims.maintenance.dto.SparePartDtos.IssueSparePartRequest;
import com.mfg.ims.maintenance.dto.SparePartDtos.SparePartIssueLineDto;
import com.mfg.ims.maintenance.service.MaintenanceCostPoster;
import com.mfg.ims.maintenance.service.MaintenanceCostService;
import com.mfg.ims.maintenance.service.MaintenanceService;
import com.mfg.ims.maintenance.service.SparePartService;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceService service;
    private final SparePartService spareParts;
    private final MaintenanceCostService costs;
    private final MaintenanceCostPoster costPoster;

    public MaintenanceController(MaintenanceService service, SparePartService spareParts,
                                 MaintenanceCostService costs, MaintenanceCostPoster costPoster) {
        this.service = service;
        this.spareParts = spareParts;
        this.costs = costs;
        this.costPoster = costPoster;
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAuthority('MAINTENANCE_VIEW')")
    public Page<MaintenanceRequestDto> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestType,
            @PageableDefault(size = 50, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
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

    @GetMapping("/requests/{id}/activities")
    @PreAuthorize("hasAuthority('MAINTENANCE_VIEW')")
    public List<ActivityDto> activities(@PathVariable Long id) {
        return service.listActivities(id);
    }

    @PostMapping("/requests/{id}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public ActivityDto addActivity(@PathVariable Long id, @Valid @RequestBody RecordActivityRequest req) {
        return service.addActivity(id, req);
    }

    @GetMapping("/requests/{id}/spare-parts")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_VIEW','SPARE_PART_VIEW')")
    public List<SparePartIssueLineDto> sparePartIssues(@PathVariable Long id) {
        return spareParts.issuesOf(id);
    }

    @PostMapping("/requests/{id}/spare-parts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SPARE_PART_ISSUE')")
    public SparePartIssueLineDto issueSparePart(@PathVariable Long id, @Valid @RequestBody IssueSparePartRequest req) {
        return spareParts.issue(id, req);
    }

    @GetMapping("/requests/{id}/cost")
    @PreAuthorize("hasAuthority('MAINTENANCE_COST_VIEW')")
    public MaintenanceCostDto cost(@PathVariable Long id) {
        return costs.cost(id);
    }

    @PostMapping("/requests/{id}/post-cost")
    @PreAuthorize("hasAuthority('MAINTENANCE_COST_POST')")
    public MaintenanceCostDto postCost(@PathVariable Long id) {
        return costs.postLabourCost(id);
    }

    @GetMapping("/costs/unposted")
    @PreAuthorize("hasAuthority('MAINTENANCE_COST_VIEW')")
    public List<MaintenanceCostDto> unpostedCosts() {
        return costs.unposted();
    }

    @PostMapping("/costs/repost-unposted")
    @PreAuthorize("hasAuthority('MAINTENANCE_COST_POST')")
    public int repostUnposted() {
        return costPoster.repostUnposted();
    }
}
