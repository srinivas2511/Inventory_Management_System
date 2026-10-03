package com.springmfg.ims.hrm.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.springmfg.ims.hrm.domain.AttendanceStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class AttendanceDtos {

    /** Status of an employee with no record for the day in the daily view. */
    public static final String NOT_RECORDED = "NOT_RECORDED";

    private AttendanceDtos() {
    }

    public record AttendanceDto(
            Long id,
            Long employeeId,
            String employeeCode,
            String employeeName,
            LocalDate attendanceDate,
            String shift,
            Instant checkIn,
            Instant checkOut,
            String status,
            BigDecimal workHours,
            boolean late,
            String source,
            String remarks) {}

    public record RecordAttendanceRequest(
            @NotNull Long employeeId,
            @NotNull LocalDate attendanceDate,
            @Size(max = 10) String shift,
            Instant checkIn,
            Instant checkOut,
            @NotNull AttendanceStatus status,
            @Size(max = 200) String remarks) {}

    public record BulkAttendanceRequest(@NotEmpty @Valid List<RecordAttendanceRequest> entries) {}

    public record AttendanceSummaryDto(
            Long employeeId,
            String employeeCode,
            String employeeName,
            int present,
            int absent,
            int halfDay,
            int leave,
            int holiday,
            int lateCount,
            BigDecimal workHours) {}

    public record ShiftDto(
            Long id,
            String shiftCode,
            String name,
            LocalTime startTime,
            LocalTime endTime,
            int graceMinutes,
            boolean active) {}

    public record SaveShiftRequest(
            @NotBlank @Size(max = 10) String shiftCode,
            @NotBlank @Size(max = 60) String name,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @PositiveOrZero Integer graceMinutes,
            Boolean active) {}
}
