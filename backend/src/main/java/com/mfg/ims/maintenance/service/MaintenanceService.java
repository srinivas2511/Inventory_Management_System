package com.mfg.ims.maintenance.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.maintenance.domain.MaintenanceActivity;
import com.mfg.ims.maintenance.domain.MaintenanceRequest;
import com.mfg.ims.maintenance.domain.MaintenanceStatus;
import com.mfg.ims.maintenance.domain.PmSchedule;
import com.mfg.ims.maintenance.dto.CreateMaintenanceRequestDto;
import com.mfg.ims.maintenance.dto.MaintenanceRequestDto;
import com.mfg.ims.maintenance.dto.MaintenanceWorkDtos.ActivityDto;
import com.mfg.ims.maintenance.dto.MaintenanceWorkDtos.RecordActivityRequest;
import com.mfg.ims.maintenance.dto.PmScheduleDto;
import com.mfg.ims.maintenance.event.MaintenanceCompletedEvent;
import com.mfg.ims.maintenance.repository.MaintenanceActivityRepository;
import com.mfg.ims.maintenance.repository.MaintenanceRequestRepository;
import com.mfg.ims.maintenance.repository.PmScheduleRepository;
import com.mfg.ims.masterdata.machine.MachineRepository;

@Service
public class MaintenanceService {

    private final MaintenanceRequestRepository requestRepo;
    private final PmScheduleRepository pmRepo;
    private final MaintenanceActivityRepository activityRepo;
    private final MachineRepository machineRepo;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final MaintenanceCostService costService;
    private final ApplicationEventPublisher eventPublisher;

    public MaintenanceService(MaintenanceRequestRepository requestRepo, PmScheduleRepository pmRepo,
                              MaintenanceActivityRepository activityRepo, MachineRepository machineRepo,
                              DocumentNumberService numberService, AuditService auditService,
                              MaintenanceCostService costService, ApplicationEventPublisher eventPublisher) {
        this.requestRepo = requestRepo;
        this.pmRepo = pmRepo;
        this.activityRepo = activityRepo;
        this.machineRepo = machineRepo;
        this.numberService = numberService;
        this.auditService = auditService;
        this.costService = costService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public MaintenanceRequestDto create(CreateMaintenanceRequestDto dto) {
        if (!machineRepo.existsById(dto.machineId())) {
            throw new NotFoundException("Machine not found: " + dto.machineId());
        }
        MaintenanceRequest r = new MaintenanceRequest();
        r.setRequestNumber(numberService.next("MR"));
        r.setMachineId(dto.machineId());
        r.setRequestType(dto.requestType());
        r.setPriority(dto.priority() > 0 ? dto.priority() : 3);
        r.setDescription(dto.description());
        r.setAssignedTo(dto.assignedTo());
        r.setScheduledDate(dto.scheduledDate());
        r.setRequestedBy(currentUserId());
        return MaintenanceRequestDto.from(requestRepo.save(r));
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceRequestDto> list(String status, String requestType, Pageable pageable) {
        return requestRepo.search(status, requestType, pageable).map(MaintenanceRequestDto::from);
    }

    @Transactional(readOnly = true)
    public MaintenanceRequestDto get(Long id) {
        return MaintenanceRequestDto.from(require(id));
    }

    /** Moves a request along OPEN -> IN_PROGRESS -> COMPLETED (or to CANCELLED); completing it fixes its labour cost. */
    @Transactional
    public MaintenanceRequestDto updateStatus(Long id, String newStatus, String resolutionNotes) {
        MaintenanceRequest r = require(id);
        MaintenanceStatus from = MaintenanceStatus.parse(r.getStatus());
        MaintenanceStatus to = from.transitionTo(MaintenanceStatus.parse(newStatus));
        r.setStatus(to.name());
        if (resolutionNotes != null) {
            r.setResolutionNotes(resolutionNotes);
        }
        if (to == MaintenanceStatus.COMPLETED) {
            r.setCompletedAt(Instant.now());
            costService.snapshotLabour(r);
        }
        r = requestRepo.save(r);
        auditService.record("MAINTENANCE_STATUS", "MaintenanceRequest", String.valueOf(id), from.name(), to.name(),
                resolutionNotes);
        if (to == MaintenanceStatus.COMPLETED) {
            eventPublisher.publishEvent(new MaintenanceCompletedEvent(id));
        }
        return MaintenanceRequestDto.from(r);
    }

    @Transactional
    public ActivityDto addActivity(Long requestId, RecordActivityRequest req) {
        MaintenanceRequest r = require(requestId);
        if (!MaintenanceStatus.parse(r.getStatus()).acceptsWork()) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Work cannot be booked on " + r.getRequestNumber() + " because it is " + r.getStatus());
        }
        MaintenanceActivity a = new MaintenanceActivity();
        a.setMaintenanceRequestId(requestId);
        a.setDescription(req.description());
        a.setDurationMinutes(req.durationMinutes());
        a.setPerformedAt(req.performedAt() != null ? req.performedAt() : Instant.now());
        a.setPerformedBy(req.performedBy() != null ? req.performedBy() : currentUserId());
        a.setSparePartsUsed(req.sparePartsUsed());
        a = activityRepo.save(a);
        auditService.record("MAINTENANCE_ACTIVITY", "MaintenanceRequest", String.valueOf(requestId), null,
                req.durationMinutes() + " min: " + req.description(), null);
        return ActivityDto.from(a);
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> listActivities(Long requestId) {
        require(requestId);
        return activityRepo.findByMaintenanceRequestIdOrderByPerformedAtAscIdAsc(requestId).stream()
                .map(ActivityDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PmScheduleDto> listPmSchedules() {
        return pmRepo.findByActiveTrueOrderByNextDueDateAsc().stream().map(PmScheduleDto::from).toList();
    }

    @Transactional
    public PmScheduleDto createPmSchedule(Long machineId, String taskName, int frequencyDays, Long assignedTo) {
        PmSchedule s = new PmSchedule();
        s.setMachineId(machineId);
        s.setTaskName(taskName);
        s.setFrequencyDays(frequencyDays);
        s.setNextDueDate(LocalDate.now().plusDays(frequencyDays));
        s.setAssignedTo(assignedTo);
        return PmScheduleDto.from(pmRepo.save(s));
    }

    @Transactional
    public MaintenanceRequestDto generatePmRequest(Long scheduleId) {
        PmSchedule s = pmRepo.findById(scheduleId)
                .orElseThrow(() -> new NotFoundException("PmSchedule", scheduleId));
        CreateMaintenanceRequestDto dto = new CreateMaintenanceRequestDto(
                s.getMachineId(), "PREVENTIVE", 3,
                "Scheduled PM: " + s.getTaskName(), s.getAssignedTo(), s.getNextDueDate());
        MaintenanceRequestDto created = create(dto);
        s.setLastPerformedDate(LocalDate.now());
        s.setNextDueDate(LocalDate.now().plusDays(s.getFrequencyDays()));
        pmRepo.save(s);
        return created;
    }

    private MaintenanceRequest require(Long id) {
        return requestRepo.findById(id).orElseThrow(() -> new NotFoundException("MaintenanceRequest", id));
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) {
            return u.id();
        }
        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                "A maintenance record needs an authenticated user to attribute it to");
    }
}
