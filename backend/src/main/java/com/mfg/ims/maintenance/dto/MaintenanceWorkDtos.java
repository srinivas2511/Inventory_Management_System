package com.mfg.ims.maintenance.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.mfg.ims.maintenance.domain.MaintenanceActivity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Work booked on a maintenance request and the cost that work adds up to. */
public final class MaintenanceWorkDtos {

    private MaintenanceWorkDtos() {
    }

    public record RecordActivityRequest(
            @NotBlank @Size(max = 500) String description,
            @NotNull @Min(1) @Max(10080) Integer durationMinutes,
            Instant performedAt,
            Long performedBy,
            @Size(max = 300) String sparePartsUsed) {
    }

    public record ActivityDto(
            Long id,
            Long maintenanceRequestId,
            String description,
            Long performedBy,
            Instant performedAt,
            Integer durationMinutes,
            String sparePartsUsed) {

        public static ActivityDto from(MaintenanceActivity a) {
            return new ActivityDto(a.getId(), a.getMaintenanceRequestId(), a.getDescription(), a.getPerformedBy(),
                    a.getPerformedAt(), a.getDurationMinutes(), a.getSparePartsUsed());
        }
    }

    /** The cost of one request: labour at the machine's cost centre rate plus the spare parts issued to it. */
    public record MaintenanceCostDto(
            Long requestId,
            String requestNumber,
            Long machineId,
            String status,
            Long costCentreId,
            long labourMinutes,
            BigDecimal labourHours,
            BigDecimal labourRatePerHour,
            BigDecimal labourCost,
            BigDecimal sparePartsCost,
            BigDecimal totalCost,
            boolean labourCostFinal,
            Long labourJournalHeaderId,
            Instant costPostedAt) {
    }
}
