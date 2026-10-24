package com.mfg.ims.maintenance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.insight.alert.AlertService;
import com.mfg.ims.maintenance.domain.MachineSensorReading;
import com.mfg.ims.maintenance.domain.MaintenancePrediction;
import com.mfg.ims.maintenance.dto.PredictiveMaintenanceDtos.MachineRiskRow;
import com.mfg.ims.maintenance.dto.PredictiveMaintenanceDtos.PredictionDto;
import com.mfg.ims.maintenance.dto.PredictiveMaintenanceDtos.PredictionSummary;
import com.mfg.ims.maintenance.dto.PredictiveMaintenanceDtos.RecordSensorReadingRequest;
import com.mfg.ims.maintenance.dto.PredictiveMaintenanceDtos.RunPredictionsResponse;
import com.mfg.ims.maintenance.dto.PredictiveMaintenanceDtos.SensorReadingDto;
import com.mfg.ims.maintenance.repository.MachineSensorReadingRepository;
import com.mfg.ims.maintenance.repository.MaintenancePredictionRepository;

@Service
public class PredictiveMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(PredictiveMaintenanceService.class);

    // Alert when predicted failure is within this many days
    private static final int ALERT_HORIZON_DAYS = 14;
    // Lookback window for downtime analysis
    private static final int LOOKBACK_DAYS = 90;
    // Minimum failures required to generate a prediction
    private static final int MIN_FAILURES = 2;

    private final MaintenancePredictionRepository predictionRepo;
    private final MachineSensorReadingRepository sensorRepo;
    private final AlertService alertService;

    public PredictiveMaintenanceService(
        MaintenancePredictionRepository predictionRepo,
        MachineSensorReadingRepository sensorRepo,
        AlertService alertService
    ) {
        this.predictionRepo = predictionRepo;
        this.sensorRepo = sensorRepo;
        this.alertService = alertService;
    }

    public List<PredictionDto> getActivePredictions() {
        return predictionRepo.findByAcknowledgedFalseOrderByPredictedFailureAtAsc()
            .stream().map(this::toDto).toList();
    }

    public List<PredictionDto> getAllPredictions() {
        return predictionRepo.findAllByOrderByGeneratedAtDesc()
            .stream().map(this::toDto).toList();
    }

    @Transactional
    public PredictionDto acknowledge(Long id, Long userId) {
        MaintenancePrediction p = predictionRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Prediction not found: " + id));
        p.setAcknowledged(true);
        p.setAcknowledgedBy(userId);
        p.setAcknowledgedAt(Instant.now());
        return toDto(predictionRepo.save(p));
    }

    /** Run MTBF-based predictions across all machines with sufficient downtime history. */
    @Transactional
    public RunPredictionsResponse runPredictions() {
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<Object[]> rows = predictionRepo.findMachineFailureStats(since, MIN_FAILURES);

        List<MachineRiskRow> machineRows = new ArrayList<>();
        int highRisk = 0;

        for (Object[] row : rows) {
            Long machineId       = ((Number) row[0]).longValue();
            String machineCode   = (String) row[1];
            String machineName   = (String) row[2];
            int failureCount     = ((Number) row[3]).intValue();
            BigDecimal mtbfHours = row[4] != null ? new BigDecimal(row[4].toString()) : null;
            Instant lastFailure  = row[5] instanceof Instant i ? i : Instant.parse(row[5].toString());

            if (mtbfHours == null || mtbfHours.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Predicted next failure = lastFailure + MTBF
            long mtbfSeconds = mtbfHours.multiply(BigDecimal.valueOf(3600)).longValue();
            Instant predictedAt = lastFailure.plusSeconds(mtbfSeconds);

            // Confidence decreases with fewer data points; scales 50–95%
            BigDecimal confidence = BigDecimal.valueOf(
                Math.min(95.0, 50.0 + (failureCount - 2) * 5.0)
            ).setScale(2, RoundingMode.HALF_UP);

            String riskLevel;
            long daysUntilFailure = ChronoUnit.DAYS.between(Instant.now(), predictedAt);
            if (daysUntilFailure <= 7)       riskLevel = "HIGH";
            else if (daysUntilFailure <= 21) riskLevel = "MEDIUM";
            else                             riskLevel = "LOW";

            if ("HIGH".equals(riskLevel)) highRisk++;

            // Upsert prediction — replace any active prediction for this machine
            predictionRepo.findTopByMachineIdAndAcknowledgedFalseOrderByGeneratedAtDesc(machineId)
                .ifPresent(predictionRepo::delete);

            MaintenancePrediction pred = new MaintenancePrediction();
            pred.setMachineId(machineId);
            pred.setPredictedFailureAt(predictedAt);
            pred.setConfidencePct(confidence);
            pred.setMtbfHours(mtbfHours);
            pred.setFailureCount(failureCount);
            pred.setLastFailureAt(lastFailure);
            pred.setGeneratedAt(Instant.now());
            predictionRepo.save(pred);

            // Raise alert when failure is within the alert horizon
            if (daysUntilFailure <= ALERT_HORIZON_DAYS) {
                String severity = "HIGH".equals(riskLevel) ? "HIGH" : "MEDIUM";
                alertService.raise("PREDICTED_FAILURE", severity, "MACHINE", machineId,
                    String.format("Machine %s (%s) predicted to fail within %d days (MTBF %.1f h, %d failures in 90 d)",
                        machineCode, machineName, Math.max(0, daysUntilFailure),
                        mtbfHours, failureCount));
            }

            log.info("Prediction: machine={} ({}) predictedAt={} confidence={}% risk={}",
                machineCode, machineName, predictedAt, confidence, riskLevel);

            machineRows.add(new MachineRiskRow(
                machineId, machineCode, machineName, failureCount,
                mtbfHours, predictedAt, confidence, riskLevel));
        }

        PredictionSummary summary = new PredictionSummary(
            rows.size(), machineRows.size(), highRisk, 0);
        return new RunPredictionsResponse(summary, machineRows);
    }

    @Transactional
    public SensorReadingDto recordSensorReading(RecordSensorReadingRequest req) {
        MachineSensorReading r = new MachineSensorReading();
        r.setMachineId(req.machineId());
        r.setReadingType(req.readingType());
        r.setReadingValue(req.readingValue());
        r.setUnit(req.unit());
        r.setSource("MANUAL");
        MachineSensorReading saved = sensorRepo.save(r);
        return toSensorDto(saved);
    }

    public List<SensorReadingDto> getSensorReadings(Long machineId) {
        return sensorRepo.findTop50ByMachineIdOrderByReadAtDesc(machineId)
            .stream().map(this::toSensorDto).toList();
    }

    /** Returns the latest risk summary row per machine derived from active predictions. */
    public List<MachineRiskRow> getMachineRisk() {
        return predictionRepo.findByAcknowledgedFalseOrderByPredictedFailureAtAsc()
            .stream()
            .map(p -> {
                long daysUntil = ChronoUnit.DAYS.between(Instant.now(), p.getPredictedFailureAt());
                String risk;
                if (daysUntil <= 7)       risk = "HIGH";
                else if (daysUntil <= 21) risk = "MEDIUM";
                else                      risk = "LOW";
                return new MachineRiskRow(
                    p.getMachineId(), null, null,
                    p.getFailureCount(), p.getMtbfHours(),
                    p.getPredictedFailureAt(), p.getConfidencePct(), risk);
            }).toList();
    }

    // ── private mappers ────────────────────────────────────────────────────────

    private PredictionDto toDto(MaintenancePrediction p) {
        return new PredictionDto(
            p.getId(), p.getMachineId(),
            null, null,  // machineCode and machineName resolved client-side via machines list
            p.getPredictedFailureAt(), p.getConfidencePct(),
            p.getMtbfHours(), p.getFailureCount(), p.getLastFailureAt(),
            p.getAlgorithmVersion(), p.getGeneratedAt(),
            p.isAcknowledged(), p.getAcknowledgedAt(), p.getNotes()
        );
    }

    private SensorReadingDto toSensorDto(MachineSensorReading r) {
        return new SensorReadingDto(
            r.getId(), r.getMachineId(), r.getReadingType(),
            r.getReadingValue(), r.getUnit(), r.getReadAt(), r.getSource()
        );
    }
}
