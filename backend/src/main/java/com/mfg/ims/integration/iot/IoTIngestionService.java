package com.mfg.ims.integration.iot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.maintenance.domain.MachineSensorReading;
import com.mfg.ims.maintenance.repository.MachineSensorReadingRepository;

@Service
public class IoTIngestionService {

    public record MachineEventPayload(
        Long machineId,
        String eventType,
        String readingType,
        BigDecimal value,
        String unit,
        Instant timestamp
    ) {}

    public record BulkIngestionRequest(List<MachineEventPayload> events) {}

    public record BulkIngestionResponse(int accepted, int rejected, List<String> errors) {}

    private final MachineSensorReadingRepository sensorRepo;

    public IoTIngestionService(MachineSensorReadingRepository sensorRepo) {
        this.sensorRepo = sensorRepo;
    }

    @Transactional
    public BulkIngestionResponse ingest(List<MachineEventPayload> events) {
        int accepted = 0;
        int rejected = 0;
        var errors = new ArrayList<String>();

        for (MachineEventPayload event : events) {
            try {
                if ("SENSOR_READING".equals(event.eventType())) {
                    MachineSensorReading r = new MachineSensorReading();
                    r.setMachineId(event.machineId());
                    r.setReadingType(event.readingType() != null ? event.readingType() : "TEMPERATURE");
                    r.setReadingValue(event.value() != null ? event.value() : BigDecimal.ZERO);
                    r.setUnit(event.unit());
                    r.setReadAt(event.timestamp() != null ? event.timestamp() : Instant.now());
                    r.setSource("IOT");
                    sensorRepo.save(r);
                    accepted++;
                } else {
                    // DOWNTIME_START / DOWNTIME_END / STATUS_CHANGE — accepted but not auto-posted;
                    // supervisor confirmation required per business rules.
                    accepted++;
                }
            } catch (Exception e) {
                rejected++;
                errors.add("machineId=" + event.machineId() + ": " + e.getMessage());
            }
        }
        return new BulkIngestionResponse(accepted, rejected, errors);
    }
}
