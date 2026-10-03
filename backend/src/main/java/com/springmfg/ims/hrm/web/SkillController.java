package com.springmfg.ims.hrm.web;

import java.util.List;

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

import com.springmfg.ims.hrm.dto.EmployeeDtos.EmployeeSummaryDto;
import com.springmfg.ims.hrm.dto.SkillTrainingDtos.SaveSkillRequest;
import com.springmfg.ims.hrm.dto.SkillTrainingDtos.SkillDto;
import com.springmfg.ims.hrm.service.SkillService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/hrm/skills")
public class SkillController {

    private final SkillService service;

    public SkillController(SkillService service) {
        this.service = service;
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public List<SkillDto> list(@PathVariable Long employeeId) {
        return service.list(employeeId);
    }

    @PostMapping("/employee/{employeeId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public SkillDto add(@PathVariable Long employeeId, @Valid @RequestBody SaveSkillRequest req) {
        return service.add(employeeId, req);
    }

    @PutMapping("/employee/{employeeId}/{skillId}")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public SkillDto update(@PathVariable Long employeeId, @PathVariable Long skillId,
                           @Valid @RequestBody SaveSkillRequest req) {
        return service.update(employeeId, skillId, req);
    }

    @DeleteMapping("/employee/{employeeId}/{skillId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public void remove(@PathVariable Long employeeId, @PathVariable Long skillId) {
        service.remove(employeeId, skillId);
    }

    /** Operators authorised for a machine and/or operation; the scheduling hook for R18. */
    @GetMapping("/authorised")
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public List<EmployeeSummaryDto> authorised(@RequestParam(required = false) Long machineId,
                                               @RequestParam(required = false) Long operationId) {
        return service.authorised(machineId, operationId);
    }
}
