package com.mfg.ims.insight.traceability;

import java.time.Instant;
import java.util.List;

public record TraceabilityDto(
        String queryType,
        Long batchId,
        String batchNumber,
        List<TraceStep> trail) {

    public record TraceStep(String stepType, String reference, String description, Instant timestamp) {}
}
