package com.mfg.ims.hrm.web;

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

import com.mfg.ims.hrm.dto.SkillTrainingDtos.SaveTrainingRequest;
import com.mfg.ims.hrm.dto.SkillTrainingDtos.TrainingDto;
import com.mfg.ims.hrm.service.TrainingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/hrm/training")
public class TrainingController {

    private final TrainingService service;

    public TrainingController(TrainingService service) {
        this.service = service;
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public List<TrainingDto> list(@PathVariable Long employeeId) {
        return service.list(employeeId);
    }

    @GetMapping("/renewals-due")
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public List<TrainingDto> renewalsDue(@RequestParam(defaultValue = "30") int days) {
        return service.renewalsDue(days);
    }

    @PostMapping("/employee/{employeeId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public TrainingDto create(@PathVariable Long employeeId, @Valid @RequestBody SaveTrainingRequest req) {
        return service.create(employeeId, req);
    }

    @PutMapping("/employee/{employeeId}/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public TrainingDto update(@PathVariable Long employeeId, @PathVariable Long id,
                              @Valid @RequestBody SaveTrainingRequest req) {
        return service.update(employeeId, id, req);
    }

    @DeleteMapping("/employee/{employeeId}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public void delete(@PathVariable Long employeeId, @PathVariable Long id) {
        service.delete(employeeId, id);
    }
}
