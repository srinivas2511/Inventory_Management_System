package com.springmfg.ims.maintenance;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.maintenance.domain.MaintenanceRequest;
import com.springmfg.ims.maintenance.dto.CreateMaintenanceRequestDto;
import com.springmfg.ims.maintenance.dto.MaintenanceRequestDto;
import com.springmfg.ims.maintenance.repository.MaintenanceRequestRepository;
import com.springmfg.ims.maintenance.repository.PmScheduleRepository;
import com.springmfg.ims.maintenance.service.MaintenanceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock MaintenanceRequestRepository requestRepo;
    @Mock PmScheduleRepository pmRepo;

    MaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceService(requestRepo, pmRepo);
        SecurityContextHolder.clearContext();
        // Simulate anonymous context so currentUserId() falls through to default 1L
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("anon", null));
    }

    @Test
    void create_persists_and_returns_dto() {
        MaintenanceRequest saved = new MaintenanceRequest();
        saved.setRequestNumber("MR-TEST");
        saved.setMachineId(1L);
        saved.setRequestType("CORRECTIVE");
        saved.setPriority(2);
        saved.setDescription("Pump bearing noise");
        saved.setStatus("OPEN");
        saved.setRequestedBy(1L);

        when(requestRepo.save(any())).thenReturn(saved);

        CreateMaintenanceRequestDto dto = new CreateMaintenanceRequestDto(1L, "CORRECTIVE", 2, "Pump bearing noise", null, null);
        MaintenanceRequestDto result = service.create(dto);

        assertThat(result.requestNumber()).isEqualTo("MR-TEST");
        assertThat(result.status()).isEqualTo("OPEN");
        assertThat(result.requestType()).isEqualTo("CORRECTIVE");
    }

    @Test
    void updateStatus_to_completed_sets_completedAt() {
        MaintenanceRequest r = new MaintenanceRequest();
        r.setRequestNumber("MR-001");
        r.setMachineId(1L);
        r.setDescription("Test");
        r.setStatus("IN_PROGRESS");
        r.setRequestedBy(1L);

        when(requestRepo.findById(1L)).thenReturn(Optional.of(r));
        when(requestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MaintenanceRequestDto result = service.updateStatus(1L, "COMPLETED", "All done");

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.completedAt()).isNotNull();
        assertThat(result.resolutionNotes()).isEqualTo("All done");
    }
}
