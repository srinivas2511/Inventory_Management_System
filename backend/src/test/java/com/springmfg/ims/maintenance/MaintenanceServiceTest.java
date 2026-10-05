package com.springmfg.ims.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.maintenance.domain.MaintenanceRequest;
import com.springmfg.ims.maintenance.dto.CreateMaintenanceRequestDto;
import com.springmfg.ims.maintenance.dto.MaintenanceRequestDto;
import com.springmfg.ims.maintenance.dto.MaintenanceWorkDtos.RecordActivityRequest;
import com.springmfg.ims.maintenance.event.MaintenanceCompletedEvent;
import com.springmfg.ims.maintenance.repository.MaintenanceActivityRepository;
import com.springmfg.ims.maintenance.repository.MaintenanceRequestRepository;
import com.springmfg.ims.maintenance.repository.PmScheduleRepository;
import com.springmfg.ims.maintenance.service.MaintenanceCostService;
import com.springmfg.ims.maintenance.service.MaintenanceService;
import com.springmfg.ims.masterdata.machine.MachineRepository;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock MaintenanceRequestRepository requestRepo;
    @Mock PmScheduleRepository pmRepo;
    @Mock MaintenanceActivityRepository activityRepo;
    @Mock MachineRepository machineRepo;
    @Mock DocumentNumberService numberService;
    @Mock AuditService auditService;
    @Mock MaintenanceCostService costService;
    @Mock ApplicationEventPublisher eventPublisher;

    MaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceService(requestRepo, pmRepo, activityRepo, machineRepo, numberService, auditService,
                costService, eventPublisher);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(42L, "maint", true, 1, Set.of(), Set.of()), null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MaintenanceRequest request(String status) {
        MaintenanceRequest r = new MaintenanceRequest();
        r.setRequestNumber("MR/26/00001");
        r.setMachineId(1L);
        r.setDescription("Test");
        r.setStatus(status);
        r.setRequestedBy(1L);
        return r;
    }

    @Test
    void create_numbers_the_request_with_the_document_sequence_and_attributes_it_to_the_current_user() {
        when(machineRepo.existsById(1L)).thenReturn(true);
        when(numberService.next("MR")).thenReturn("MR/26/00007");
        when(requestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MaintenanceRequestDto result = service.create(
                new CreateMaintenanceRequestDto(1L, "CORRECTIVE", 2, "Pump bearing noise", null, null));

        ArgumentCaptor<MaintenanceRequest> saved = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(requestRepo).save(saved.capture());
        assertThat(saved.getValue().getRequestedBy()).isEqualTo(42L);
        assertThat(result.requestNumber()).isEqualTo("MR/26/00007");
        assertThat(result.status()).isEqualTo("OPEN");
        assertThat(result.priority()).isEqualTo(2);
    }

    @Test
    void create_for_an_unknown_machine_is_not_found() {
        when(machineRepo.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                new CreateMaintenanceRequestDto(9L, "CORRECTIVE", 3, "x", null, null)))
                .isInstanceOf(NotFoundException.class);
        verify(requestRepo, never()).save(any());
    }

    @Test
    void create_without_an_authenticated_user_is_refused_instead_of_falling_back_to_user_one() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("anon", null));
        when(machineRepo.existsById(1L)).thenReturn(true);
        when(numberService.next("MR")).thenReturn("MR/26/00008");

        assertThatThrownBy(() -> service.create(
                new CreateMaintenanceRequestDto(1L, "CORRECTIVE", 3, "x", null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("authenticated user");
        verify(requestRepo, never()).save(any());
    }

    @Test
    void completing_a_request_sets_completedAt_fixes_the_labour_cost_and_publishes_the_event() {
        MaintenanceRequest r = request("IN_PROGRESS");
        when(requestRepo.findById(1L)).thenReturn(Optional.of(r));
        when(requestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MaintenanceRequestDto result = service.updateStatus(1L, "COMPLETED", "All done");

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.completedAt()).isNotNull();
        assertThat(result.resolutionNotes()).isEqualTo("All done");
        verify(costService).snapshotLabour(r);
        verify(eventPublisher).publishEvent(new MaintenanceCompletedEvent(1L));
        verify(auditService).record("MAINTENANCE_STATUS", "MaintenanceRequest", "1", "IN_PROGRESS", "COMPLETED",
                "All done");
    }

    @Test
    void a_move_that_is_not_in_the_state_machine_is_refused_and_changes_nothing() {
        MaintenanceRequest r = request("OPEN");
        when(requestRepo.findById(1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.updateStatus(1L, "COMPLETED", null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("OPEN");
        assertThat(r.getStatus()).isEqualTo("OPEN");
        verify(requestRepo, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void an_unknown_status_value_is_a_validation_error() {
        when(requestRepo.findById(1L)).thenReturn(Optional.of(request("OPEN")));

        assertThatThrownBy(() -> service.updateStatus(1L, "DONE", null)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("DONE");
    }

    @Test
    void work_cannot_be_booked_on_a_completed_request() {
        when(requestRepo.findById(1L)).thenReturn(Optional.of(request("COMPLETED")));

        assertThatThrownBy(() -> service.addActivity(1L, new RecordActivityRequest("Late", 30, null, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("COMPLETED");
        verify(activityRepo, never()).save(any());
    }
}
