package com.mfg.ims.scheduling;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Request and response shapes of the scheduling endpoints. */
public final class SchedulingDtos {

    private SchedulingDtos() {
    }

    /**
     * Which operations a run plans. FULL replans every pending operation in scope and drops its old window;
     * ONLY_UNSCHEDULED keeps existing windows (they block the machines) and plans only operations with none.
     */
    public record RunScheduleRequest(
            @Min(1) @Max(90) Integer horizonDays,
            List<Long> productionOrderIds,
            String mode) {
    }

    public record PlacementDto(
            Long productionOperationId,
            Long productionOrderId,
            String orderNumber,
            int sequenceNo,
            String operationName,
            Long machineId,
            String machineCode,
            Instant start,
            Instant end,
            Long operatorUserId,
            List<String> warnings) {
    }

    public record UnplacedDto(
            Long productionOperationId,
            Long productionOrderId,
            String orderNumber,
            int sequenceNo,
            String operationName,
            String reason) {
    }

    public record OrderOutcomeDto(Long productionOrderId, String orderNumber, LocalDate dueDate, Instant endsAt,
                                  boolean late) {
    }

    public record SchedulePlanDto(
            boolean applied,
            String mode,
            Instant horizonStart,
            LocalDate horizonEnd,
            List<PlacementDto> placements,
            List<UnplacedDto> unplaced,
            List<OrderOutcomeDto> orders) {
    }

    public record GanttBarDto(
            Long productionOperationId,
            Long productionOrderId,
            String orderNumber,
            String productCode,
            int priority,
            int sequenceNo,
            String operationName,
            String status,
            Instant start,
            Instant end,
            Long operatorUserId,
            String operatorName,
            boolean late,
            boolean locked) {
    }

    public record GanttMaintenanceDto(Long maintenanceRequestId, String requestNumber, LocalDate date,
                                      String requestType) {
    }

    public record GanttMachineDto(
            Long machineId,
            String machineCode,
            String machineName,
            List<GanttBarDto> bars,
            List<GanttMaintenanceDto> maintenance) {
    }

    public record UnscheduledOperationDto(Long productionOperationId, Long productionOrderId, String orderNumber,
                                          int priority, int sequenceNo, String operationName, Long machineId) {
    }

    /** One business day on the chart: where it starts and ends on the time axis and whether machines run. */
    public record GanttDayDto(LocalDate date, Instant start, Instant end, boolean working) {
    }

    public record GanttDto(
            LocalDate from,
            LocalDate to,
            String zone,
            List<GanttDayDto> days,
            List<LocalDate> workingDays,
            List<GanttMachineDto> machines,
            List<UnscheduledOperationDto> unscheduled) {
    }

    public record RescheduleRequest(@NotNull Long machineId, @NotNull Instant start) {
    }

    public record RescheduleResultDto(GanttBarDto bar, Long machineId, String machineCode, List<String> warnings) {
    }
}
