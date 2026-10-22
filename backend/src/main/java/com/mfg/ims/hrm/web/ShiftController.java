package com.mfg.ims.hrm.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.hrm.dto.AttendanceDtos.SaveShiftRequest;
import com.mfg.ims.hrm.dto.AttendanceDtos.ShiftDto;
import com.mfg.ims.hrm.service.ShiftService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/hrm/shifts")
public class ShiftController {

    private final ShiftService service;

    public ShiftController(ShiftService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public List<ShiftDto> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public ShiftDto create(@Valid @RequestBody SaveShiftRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public ShiftDto update(@PathVariable Long id, @Valid @RequestBody SaveShiftRequest req) {
        return service.update(id, req);
    }
}
