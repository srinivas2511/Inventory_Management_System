package com.springmfg.ims.hrm.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.hrm.dto.AttendanceDtos.AttendanceDto;
import com.springmfg.ims.hrm.dto.AttendanceDtos.AttendanceSummaryDto;
import com.springmfg.ims.hrm.dto.AttendanceDtos.BulkAttendanceRequest;
import com.springmfg.ims.hrm.dto.AttendanceDtos.RecordAttendanceRequest;
import com.springmfg.ims.hrm.service.AttendanceService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/hrm/attendance")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public List<AttendanceDto> daily(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.daily(date);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public List<AttendanceSummaryDto> summary(@RequestParam int year, @RequestParam int month) {
        return service.summary(year, month);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public AttendanceDto record(@Valid @RequestBody RecordAttendanceRequest req) {
        return service.record(req);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public List<AttendanceDto> recordBulk(@Valid @RequestBody BulkAttendanceRequest req) {
        return service.recordBulk(req);
    }
}
