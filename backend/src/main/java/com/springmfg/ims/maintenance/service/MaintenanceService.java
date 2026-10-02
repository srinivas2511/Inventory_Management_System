package com.springmfg.ims.maintenance.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.maintenance.domain.MaintenanceRequest;
import com.springmfg.ims.maintenance.domain.PmSchedule;
import com.springmfg.ims.maintenance.dto.CreateMaintenanceRequestDto;
import com.springmfg.ims.maintenance.dto.MaintenanceRequestDto;
import com.springmfg.ims.maintenance.dto.PmScheduleDto;
import com.springmfg.ims.maintenance.repository.MaintenanceRequestRepository;
import com.springmfg.ims.maintenance.repository.PmScheduleRepository;

@Service
public class MaintenanceService {

    private final MaintenanceRequestRepository requestRepo;
    private final PmScheduleRepository pmRepo;

    public MaintenanceService(MaintenanceRequestRepository requestRepo, PmScheduleRepository pmRepo) {
        this.requestRepo = requestRepo;
        this.pmRepo = pmRepo;
    }

    @Transactional
    public MaintenanceRequestDto create(CreateMaintenanceRequestDto dto) {
        MaintenanceRequest r = new MaintenanceRequest();
        r.setRequestNumber("MR-" + System.currentTimeMillis());
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
        return MaintenanceRequestDto.from(
                requestRepo.findById(id).orElseThrow(() -> new NotFoundException("MaintenanceRequest", id)));
    }

    @Transactional
    public MaintenanceRequestDto updateStatus(Long id, String newStatus, String resolutionNotes) {
        MaintenanceRequest r = requestRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("MaintenanceRequest", id));
        r.setStatus(newStatus);
        if (resolutionNotes != null) r.setResolutionNotes(resolutionNotes);
        if ("COMPLETED".equals(newStatus)) r.setCompletedAt(Instant.now());
        return MaintenanceRequestDto.from(requestRepo.save(r));
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

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return 1L;
    }
}
