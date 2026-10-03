package com.springmfg.ims.hrm.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.hrm.dto.LeaveDtos.ApplyLeaveRequest;
import com.springmfg.ims.hrm.dto.LeaveDtos.LeaveApplicationDto;
import com.springmfg.ims.hrm.dto.LeaveDtos.LeaveBalanceDto;
import com.springmfg.ims.hrm.dto.LeaveDtos.LeaveDecisionRequest;
import com.springmfg.ims.hrm.dto.LeaveDtos.LeaveTypeDto;
import com.springmfg.ims.hrm.service.LeaveService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/hrm/leave")
public class LeaveController {

    private final LeaveService service;

    public LeaveController(LeaveService service) {
        this.service = service;
    }

    @GetMapping("/types")
    @PreAuthorize("hasAnyAuthority('HR_VIEW','LEAVE_APPLY','LEAVE_APPROVE')")
    public List<LeaveTypeDto> types() {
        return service.types();
    }

    /** Without employeeId: the caller's own balances. */
    @GetMapping("/balances")
    @PreAuthorize("hasAnyAuthority('HR_VIEW','LEAVE_APPLY','LEAVE_APPROVE')")
    public List<LeaveBalanceDto> balances(@RequestParam(required = false) Long employeeId,
                                          @RequestParam(required = false) Integer year) {
        return service.balances(employeeId, year);
    }

    /** Approvers and HR see everyone's applications; everyone else sees only their own. */
    @GetMapping("/applications")
    @PreAuthorize("hasAnyAuthority('HR_VIEW','LEAVE_APPLY','LEAVE_APPROVE')")
    public Page<LeaveApplicationDto> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) Long employeeId,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @PageableDefault(size = 50, sort = "fromDate",
                                                  direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(status, employeeId, from, to, pageable);
    }

    @GetMapping("/applications/{id}")
    @PreAuthorize("hasAnyAuthority('HR_VIEW','LEAVE_APPLY','LEAVE_APPROVE')")
    public LeaveApplicationDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LEAVE_APPLY')")
    public LeaveApplicationDto apply(@Valid @RequestBody ApplyLeaveRequest req) {
        return service.apply(req);
    }

    @PostMapping("/applications/{id}/approve")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public LeaveApplicationDto approve(@PathVariable Long id, @Valid @RequestBody(required = false) LeaveDecisionRequest req) {
        return service.approve(id, req == null ? null : req.reason());
    }

    @PostMapping("/applications/{id}/reject")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public LeaveApplicationDto reject(@PathVariable Long id, @Valid @RequestBody LeaveDecisionRequest req) {
        return service.reject(id, req.reason());
    }

    @PostMapping("/applications/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('LEAVE_APPLY','LEAVE_APPROVE')")
    public LeaveApplicationDto cancel(@PathVariable Long id, @Valid @RequestBody(required = false) LeaveDecisionRequest req) {
        return service.cancel(id, req == null ? null : req.reason());
    }
}
