package com.mfg.ims.hrm.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ConflictException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.hrm.domain.Employee;
import com.mfg.ims.hrm.domain.TrainingRecord;
import com.mfg.ims.hrm.dto.SkillTrainingDtos.SaveTrainingRequest;
import com.mfg.ims.hrm.dto.SkillTrainingDtos.TrainingDto;
import com.mfg.ims.hrm.repository.EmployeeRepository;
import com.mfg.ims.hrm.repository.TrainingRecordRepository;

/** Training programmes attended, certifications gained and the dates they must be renewed (HRM-006). */
@Service
public class TrainingService {

    private final TrainingRecordRepository repo;
    private final EmployeeRepository employeeRepo;
    private final AuditService auditService;

    public TrainingService(TrainingRecordRepository repo, EmployeeRepository employeeRepo, AuditService auditService) {
        this.repo = repo;
        this.employeeRepo = employeeRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TrainingDto> list(Long employeeId) {
        Employee e = requireEmployee(employeeId);
        return repo.findByEmployeeIdOrderByStartDateDesc(employeeId).stream().map(t -> toDto(t, e)).toList();
    }

    /** Completed trainings whose renewal falls within the next {@code days} days (or is already overdue). */
    @Transactional(readOnly = true)
    public List<TrainingDto> renewalsDue(int days) {
        List<TrainingRecord> due = repo.findRenewalsDueBy(LocalDate.now().plusDays(Math.max(days, 0)));
        Map<Long, Employee> employees = employeeRepo.findAllById(due.stream().map(TrainingRecord::getEmployeeId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
        return due.stream().map(t -> toDto(t, employees.get(t.getEmployeeId()))).toList();
    }

    @Transactional
    public TrainingDto create(Long employeeId, SaveTrainingRequest req) {
        Employee e = requireEmployee(employeeId);
        TrainingRecord t = new TrainingRecord();
        t.setEmployeeId(employeeId);
        apply(t, req);
        t = repo.save(t);
        auditService.record("TRAINING_CREATE", "TRAINING", String.valueOf(t.getId()), null,
                t.getProgrammeName() + " for " + e.getEmployeeCode(), null);
        return toDto(t, e);
    }

    @Transactional
    public TrainingDto update(Long employeeId, Long id, SaveTrainingRequest req) {
        Employee e = requireEmployee(employeeId);
        TrainingRecord t = require(employeeId, id);
        if (req.version() != null && !req.version().equals(t.getVersion())) {
            throw new ConflictException(ErrorCode.VERSION_CONFLICT, "Training record was changed by someone else");
        }
        String old = t.getProgrammeName() + "/" + t.getStatus();
        apply(t, req);
        t = repo.save(t);
        auditService.record("TRAINING_UPDATE", "TRAINING", String.valueOf(id), old,
                t.getProgrammeName() + "/" + t.getStatus(), null);
        return toDto(t, e);
    }

    @Transactional
    public void delete(Long employeeId, Long id) {
        requireEmployee(employeeId);
        TrainingRecord t = require(employeeId, id);
        repo.delete(t);
        auditService.record("TRAINING_DELETE", "TRAINING", String.valueOf(id), t.getProgrammeName(), null, null);
    }

    private void apply(TrainingRecord t, SaveTrainingRequest req) {
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "End date is before the start date");
        }
        if (req.renewalDueOn() != null && req.certifiedOn() != null && req.renewalDueOn().isBefore(req.certifiedOn())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Renewal date is before the certification date");
        }
        t.setProgrammeName(req.programmeName().trim());
        t.setTrainingType(req.trainingType().name());
        t.setProvider(blank(req.provider()));
        t.setStartDate(req.startDate());
        t.setEndDate(req.endDate());
        t.setHours(req.hours());
        t.setStatus(req.status().name());
        t.setScore(req.score());
        t.setCertificateNo(blank(req.certificateNo()));
        t.setCertifiedOn(req.certifiedOn());
        t.setRenewalDueOn(req.renewalDueOn());
        t.setNotes(blank(req.notes()));
    }

    private TrainingRecord require(Long employeeId, Long id) {
        TrainingRecord t = repo.findById(id).orElseThrow(() -> new NotFoundException("Training", id));
        if (!t.getEmployeeId().equals(employeeId)) {
            throw new NotFoundException("Training", id);
        }
        return t;
    }

    private Employee requireEmployee(Long employeeId) {
        return employeeRepo.findById(employeeId).orElseThrow(() -> new NotFoundException("Employee", employeeId));
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static TrainingDto toDto(TrainingRecord t, Employee e) {
        return new TrainingDto(t.getId(), t.getEmployeeId(), e == null ? null : e.getEmployeeCode(),
                e == null ? null : e.fullName(), t.getProgrammeName(), t.getTrainingType(), t.getProvider(),
                t.getStartDate(), t.getEndDate(), t.getHours(), t.getStatus(), t.getScore(), t.getCertificateNo(),
                t.getCertifiedOn(), t.getRenewalDueOn(), t.getNotes(), t.getVersion());
    }
}
