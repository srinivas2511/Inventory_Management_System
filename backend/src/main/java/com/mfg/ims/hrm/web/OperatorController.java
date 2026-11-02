package com.mfg.ims.hrm.web;

import java.util.List;

import jakarta.validation.Valid;

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

import com.mfg.ims.hrm.dto.OperatorDtos.OperatorListDto;
import com.mfg.ims.hrm.dto.OperatorDtos.SaveOperatorRequest;
import com.mfg.ims.hrm.service.EmployeeService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/hrm/operators")
@Tag(name = "Operators")
public class OperatorController {

    private final EmployeeService service;

    public OperatorController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('OPERATOR_VIEW','OPERATOR_MANAGE')")
    public List<OperatorListDto> findAll() {
        return service.findOperators();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('OPERATOR_MANAGE')")
    public OperatorListDto create(@Valid @RequestBody SaveOperatorRequest req) {
        return service.createOperator(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('OPERATOR_MANAGE')")
    public OperatorListDto update(@PathVariable Long id, @Valid @RequestBody SaveOperatorRequest req) {
        return service.updateOperator(id, req);
    }
}
