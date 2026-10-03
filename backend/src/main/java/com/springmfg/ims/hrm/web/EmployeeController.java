package com.springmfg.ims.hrm.web;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

import com.springmfg.ims.hrm.dto.EmployeeDtos.DeactivateEmployeeRequest;
import com.springmfg.ims.hrm.dto.EmployeeDtos.EmployeeDto;
import com.springmfg.ims.hrm.dto.EmployeeDtos.EmployeeSummaryDto;
import com.springmfg.ims.hrm.dto.EmployeeDtos.SaveEmployeeRequest;
import com.springmfg.ims.hrm.dto.EmployeeDtos.UnlinkedUserDto;
import com.springmfg.ims.hrm.service.EmployeeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/hrm/employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public Page<EmployeeDto> list(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) String department,
                                  @RequestParam(required = false) Boolean active,
                                  @PageableDefault(size = 50, sort = "employeeCode", direction = Sort.Direction.ASC)
                                  Pageable pageable) {
        return service.search(q, department, active, pageable);
    }

    @GetMapping("/summaries")
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public List<EmployeeSummaryDto> summaries() {
        return service.activeSummaries();
    }

    @GetMapping("/unlinked-users")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public List<UnlinkedUserDto> unlinkedUsers() {
        return service.unlinkedShopFloorUsers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public EmployeeDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public EmployeeDto create(@Valid @RequestBody SaveEmployeeRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public EmployeeDto update(@PathVariable Long id, @Valid @RequestBody SaveEmployeeRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public EmployeeDto deactivate(@PathVariable Long id, @Valid @RequestBody DeactivateEmployeeRequest req) {
        return service.deactivate(id, req);
    }
}
