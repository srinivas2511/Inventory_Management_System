package com.mfg.ims.hrm.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.hrm.dto.EmployeeDtos.EmployeeDto;
import com.mfg.ims.hrm.repository.EmployeeRepository;
import com.mfg.ims.hrm.service.EmployeeService;
import com.mfg.ims.hrm.service.HrmSecurity;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/hrm/operators")
@Tag(name = "Operators")
public class OperatorController {

    private final EmployeeRepository repo;
    private final EmployeeService service;

    public OperatorController(EmployeeRepository repo, EmployeeService service) {
        this.repo = repo;
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('OPERATOR_VIEW','OPERATOR_MANAGE')")
    public List<EmployeeDto> findAll() {
        boolean vis = HrmSecurity.canSeeCompensation();
        return repo.findByOperatorTrueOrderByOperatorCode()
                .stream().map(e -> service.toDto(e, vis)).toList();
    }
}
