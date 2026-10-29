package com.mfg.ims.production.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.production.service.CapacityBoardService;
import com.mfg.ims.production.service.CapacityBoardService.CapacityBoardDto;
import com.mfg.ims.production.service.CapacityBoardService.ScheduleRequest;
import com.mfg.ims.production.service.CapacityBoardService.ScheduledOpDto;

@RestController
@RequestMapping("/api/production")
public class CapacityBoardController {

    private final CapacityBoardService service;

    public CapacityBoardController(CapacityBoardService service) {
        this.service = service;
    }

    @GetMapping("/capacity-board")
    @PreAuthorize("hasAuthority('CAPACITY_VIEW')")
    public CapacityBoardDto getCapacityBoard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.getCapacityBoard(from, to);
    }

    @PostMapping("/schedule-operation")
    @PreAuthorize("hasAuthority('CAPACITY_MANAGE')")
    public ScheduledOpDto scheduleOperation(
            @RequestBody ScheduleRequest req,
            @AuthenticationPrincipal CachedUser user) {
        Long userId = user != null ? user.id() : 1L;
        return service.scheduleOperation(req, userId);
    }
}
