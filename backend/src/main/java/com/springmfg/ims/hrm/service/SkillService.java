package com.springmfg.ims.hrm.service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.hrm.domain.Employee;
import com.springmfg.ims.hrm.domain.EmployeeSkill;
import com.springmfg.ims.hrm.domain.SkillType;
import com.springmfg.ims.hrm.dto.EmployeeDtos.EmployeeSummaryDto;
import com.springmfg.ims.hrm.dto.SkillTrainingDtos.SaveSkillRequest;
import com.springmfg.ims.hrm.dto.SkillTrainingDtos.SkillDto;
import com.springmfg.ims.hrm.repository.EmployeeRepository;
import com.springmfg.ims.hrm.repository.EmployeeSkillRepository;
import com.springmfg.ims.masterdata.machine.MachineRepository;
import com.springmfg.ims.masterdata.operation.OperationRepository;

/** Operator skill matrix: machine authorisations, operation skills and certifications (HRM-005). */
@Service
public class SkillService {

    private final EmployeeSkillRepository repo;
    private final EmployeeRepository employeeRepo;
    private final MachineRepository machineRepo;
    private final OperationRepository operationRepo;
    private final AuditService auditService;

    public SkillService(EmployeeSkillRepository repo, EmployeeRepository employeeRepo, MachineRepository machineRepo,
                        OperationRepository operationRepo, AuditService auditService) {
        this.repo = repo;
        this.employeeRepo = employeeRepo;
        this.machineRepo = machineRepo;
        this.operationRepo = operationRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<SkillDto> list(Long employeeId) {
        requireEmployee(employeeId);
        return repo.findByEmployeeIdOrderByIdAsc(employeeId).stream().map(this::toDto).toList();
    }

    @Transactional
    public SkillDto add(Long employeeId, SaveSkillRequest req) {
        requireEmployee(employeeId);
        EmployeeSkill s = new EmployeeSkill();
        s.setEmployeeId(employeeId);
        apply(s, req);
        s = repo.save(s);
        auditService.record("SKILL_ADD", "EMPLOYEE_SKILL", String.valueOf(s.getId()), null,
                s.getSkillType() + " for employee " + employeeId, null);
        return toDto(s);
    }

    @Transactional
    public SkillDto update(Long employeeId, Long skillId, SaveSkillRequest req) {
        EmployeeSkill s = require(employeeId, skillId);
        apply(s, req);
        s = repo.save(s);
        auditService.record("SKILL_UPDATE", "EMPLOYEE_SKILL", String.valueOf(skillId), null,
                s.getSkillType() + " for employee " + employeeId, null);
        return toDto(s);
    }

    @Transactional
    public void remove(Long employeeId, Long skillId) {
        EmployeeSkill s = require(employeeId, skillId);
        repo.delete(s);
        auditService.record("SKILL_REMOVE", "EMPLOYEE_SKILL", String.valueOf(skillId),
                s.getSkillType() + " for employee " + employeeId, null, null);
    }

    /**
     * Active employees holding a non-expired authorisation for the machine and/or the operation. When both are
     * given the employee must hold both. At least one is required.
     */
    @Transactional(readOnly = true)
    public List<EmployeeSummaryDto> authorised(Long machineId, Long operationId) {
        if (machineId == null && operationId == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Give a machineId and/or an operationId");
        }
        LocalDate today = LocalDate.now();
        Set<Long> ids = null;
        if (machineId != null) {
            ids = new HashSet<>(repo.authorisedForMachine(machineId, today));
        }
        if (operationId != null) {
            Set<Long> byOperation = new HashSet<>(repo.authorisedForOperation(operationId, today));
            if (ids == null) {
                ids = byOperation;
            } else {
                ids.retainAll(byOperation);
            }
        }
        return employeeRepo.findAllById(ids).stream()
                .filter(Employee::isActive)
                .map(EmployeeService::summary)
                .sorted((a, b) -> a.employeeCode().compareTo(b.employeeCode()))
                .toList();
    }

    private void apply(EmployeeSkill s, SaveSkillRequest req) {
        SkillType type = req.skillType();
        switch (type) {
            case MACHINE -> {
                if (req.machineId() == null) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A machine skill needs machineId");
                }
                if (!machineRepo.existsById(req.machineId())) {
                    throw new NotFoundException("Machine", req.machineId());
                }
            }
            case OPERATION -> {
                if (req.operationId() == null) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "An operation skill needs operationId");
                }
                if (!operationRepo.existsById(req.operationId())) {
                    throw new NotFoundException("Operation", req.operationId());
                }
            }
            case CERTIFICATION -> {
                if (req.skillName() == null || req.skillName().isBlank()) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A certification needs skillName");
                }
            }
        }
        if (req.certifiedOn() != null && req.expiresOn() != null && req.expiresOn().isBefore(req.certifiedOn())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Expiry is before the certification date");
        }
        s.setSkillType(type.name());
        s.setMachineId(type == SkillType.MACHINE ? req.machineId() : null);
        s.setOperationId(type == SkillType.OPERATION ? req.operationId() : null);
        s.setSkillName(req.skillName() == null || req.skillName().isBlank() ? null : req.skillName().trim());
        s.setCertificationNo(req.certificationNo() == null || req.certificationNo().isBlank() ? null
                : req.certificationNo().trim());
        s.setProficiencyLevel(req.proficiencyLevel() == null ? null : req.proficiencyLevel().shortValue());
        s.setCertifiedOn(req.certifiedOn());
        s.setExpiresOn(req.expiresOn());
        s.setActive(req.active() == null || req.active());
    }

    private EmployeeSkill require(Long employeeId, Long skillId) {
        EmployeeSkill s = repo.findById(skillId).orElseThrow(() -> new NotFoundException("Skill", skillId));
        if (!s.getEmployeeId().equals(employeeId)) {
            throw new NotFoundException("Skill", skillId);
        }
        return s;
    }

    private void requireEmployee(Long employeeId) {
        if (!employeeRepo.existsById(employeeId)) {
            throw new NotFoundException("Employee", employeeId);
        }
    }

    private SkillDto toDto(EmployeeSkill s) {
        String machine = s.getMachineId() == null ? null
                : machineRepo.findById(s.getMachineId()).map(m -> m.getMachineCode() + " " + m.getName()).orElse(null);
        String operation = s.getOperationId() == null ? null
                : operationRepo.findById(s.getOperationId()).map(o -> o.getOperationCode() + " " + o.getName())
                        .orElse(null);
        boolean expired = s.getExpiresOn() != null && s.getExpiresOn().isBefore(LocalDate.now());
        return new SkillDto(s.getId(), s.getEmployeeId(), s.getSkillType(), s.getMachineId(), machine,
                s.getOperationId(), operation, s.getSkillName(), s.getCertificationNo(),
                s.getProficiencyLevel() == null ? null : s.getProficiencyLevel().intValue(), s.getCertifiedOn(),
                s.getExpiresOn(), expired, s.isActive());
    }
}
