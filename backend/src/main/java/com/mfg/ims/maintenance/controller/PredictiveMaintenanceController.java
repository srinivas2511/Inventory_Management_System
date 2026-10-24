package com.mfg.ims.maintenance.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.maintenance.dto.PredictiveMaintenanceDtos.MachineRiskRow;
import com.mfg.ims.maintenance.dto.PredictiveMaintenanceDtos.PredictionDto;
import com.mfg.ims.maintenance.dto.PredictiveMaintenanceDtos.RecordSensorReadingRequest;
import com.mfg.ims.maintenance.dto.PredictiveMaintenanceDtos.RunPredictionsResponse;
import com.mfg.ims.maintenance.dto.PredictiveMaintenanceDtos.SensorReadingDto;
import com.mfg.ims.maintenance.service.PredictiveMaintenanceService;

@RestController
@RequestMapping("/api/maintenance/predictive")
public class PredictiveMaintenanceController {

    private final PredictiveMaintenanceService service;

    public PredictiveMaintenanceController(PredictiveMaintenanceService service) {
        this.service = service;
    }

    @GetMapping("/predictions")
    @PreAuthorize("hasAuthority('PREDICTIVE_MAINT_VIEW')")
    public List<PredictionDto> getPredictions(@RequestParam(defaultValue = "false") boolean all) {
        return all ? service.getAllPredictions() : service.getActivePredictions();
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('PREDICTIVE_MAINT_VIEW')")
    public RunPredictionsResponse runPredictions() {
        return service.runPredictions();
    }

    @PostMapping("/predictions/{id}/acknowledge")
    @PreAuthorize("hasAuthority('PREDICTIVE_MAINT_VIEW')")
    public PredictionDto acknowledge(
        @PathVariable Long id,
        @AuthenticationPrincipal CachedUser principal
    ) {
        return service.acknowledge(id, principal.id());
    }

    @GetMapping("/machine-risk")
    @PreAuthorize("hasAuthority('PREDICTIVE_MAINT_VIEW')")
    public List<MachineRiskRow> getMachineRisk() {
        return service.getMachineRisk();
    }

    @GetMapping("/sensor-readings")
    @PreAuthorize("hasAuthority('PREDICTIVE_MAINT_VIEW')")
    public List<SensorReadingDto> getSensorReadings(@RequestParam Long machineId) {
        return service.getSensorReadings(machineId);
    }

    @PostMapping("/sensor-readings")
    @PreAuthorize("hasAuthority('PREDICTIVE_MAINT_VIEW')")
    public ResponseEntity<SensorReadingDto> recordSensorReading(
        @RequestBody RecordSensorReadingRequest req
    ) {
        return ResponseEntity.ok(service.recordSensorReading(req));
    }
}
