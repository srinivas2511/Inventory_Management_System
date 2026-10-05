package com.springmfg.ims.maintenance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.springmfg.ims.maintenance.domain.MaintenanceRequest;

public record MaintenanceRequestDto(
        Long id,
        String requestNumber,
        Long machineId,
        String requestType,
        int priority,
        String description,
        String status,
        Long requestedBy,
        Long assignedTo,
        LocalDate scheduledDate,
        Instant completedAt,
        String resolutionNotes,
        Instant createdAt,
        BigDecimal labourHours,
        BigDecimal labourCost,
        Long costCentreId,
        Instant costPostedAt) {

    public static MaintenanceRequestDto from(MaintenanceRequest r) {
        return new MaintenanceRequestDto(
                r.getId(), r.getRequestNumber(), r.getMachineId(),
                r.getRequestType(), r.getPriority(), r.getDescription(),
                r.getStatus(), r.getRequestedBy(), r.getAssignedTo(),
                r.getScheduledDate(), r.getCompletedAt(), r.getResolutionNotes(),
                r.getCreatedAt(), r.getLabourHours(), r.getLabourCost(), r.getCostCentreId(), r.getCostPostedAt());
    }
}
