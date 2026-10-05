package com.springmfg.ims.scheduling;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.springmfg.ims.scheduling.SchedulingDtos.GanttDto;
import com.springmfg.ims.scheduling.SchedulingDtos.RescheduleRequest;
import com.springmfg.ims.scheduling.SchedulingDtos.RescheduleResultDto;
import com.springmfg.ims.scheduling.SchedulingDtos.RunScheduleRequest;
import com.springmfg.ims.scheduling.SchedulingDtos.SchedulePlanDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/scheduling")
public class SchedulingController {

    private final SchedulingService service;

    public SchedulingController(SchedulingService service) {
        this.service = service;
    }

    @GetMapping("/gantt")
    @PreAuthorize("hasAuthority('SCHEDULE_VIEW')")
    public GanttDto gantt(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required = false) Long machineId) {
        return service.gantt(from, to, machineId);
    }

    /** With {@code dryRun=true} the plan is returned without being saved. */
    @PostMapping("/run")
    @PreAuthorize("hasAuthority('SCHEDULE_MANAGE')")
    public SchedulePlanDto run(@RequestParam(defaultValue = "false") boolean dryRun,
                               @Valid @RequestBody(required = false) RunScheduleRequest request) {
        return service.run(request, !dryRun);
    }

    @PutMapping("/operations/{operationId}/schedule")
    @PreAuthorize("hasAuthority('SCHEDULE_MANAGE')")
    public RescheduleResultDto reschedule(@PathVariable Long operationId,
                                          @Valid @RequestBody RescheduleRequest request) {
        return service.reschedule(operationId, request);
    }

    @DeleteMapping("/operations/{operationId}/schedule")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SCHEDULE_MANAGE')")
    public void unschedule(@PathVariable Long operationId) {
        service.unschedule(operationId);
    }
}
