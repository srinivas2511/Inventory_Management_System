package com.mfg.ims.maintenance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PredictiveMaintenanceDtos {

    private PredictiveMaintenanceDtos() {}

    public record PredictionDto(
        Long id,
        Long machineId,
        String machineCode,
        String machineName,
        Instant predictedFailureAt,
        BigDecimal confidencePct,
        BigDecimal mtbfHours,
        int failureCount,
        Instant lastFailureAt,
        String algorithmVersion,
        Instant generatedAt,
        boolean acknowledged,
        Instant acknowledgedAt,
        String notes
    ) {}

    public record PredictionSummary(
        int machinesAnalyzed,
        int predictionsGenerated,
        int highRiskCount,
        int acknowledgedCount
    ) {}

    public record SensorReadingDto(
        Long id,
        Long machineId,
        String readingType,
        BigDecimal readingValue,
        String unit,
        Instant readAt,
        String source
    ) {}

    public record RecordSensorReadingRequest(
        Long machineId,
        String readingType,
        BigDecimal readingValue,
        String unit
    ) {}

    public record MachineRiskRow(
        Long machineId,
        String machineCode,
        String machineName,
        int failureCountLast90Days,
        BigDecimal mtbfHours,
        Instant nextPredictedFailure,
        BigDecimal confidencePct,
        String riskLevel
    ) {}

    public record RunPredictionsResponse(
        PredictionSummary summary,
        List<MachineRiskRow> machines
    ) {}
}
