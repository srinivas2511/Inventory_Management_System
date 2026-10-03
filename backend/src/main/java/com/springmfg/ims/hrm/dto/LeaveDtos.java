package com.springmfg.ims.hrm.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class LeaveDtos {

    private LeaveDtos() {
    }

    public record LeaveTypeDto(
            Long id,
            String leaveCode,
            String name,
            Integer maxDaysPerYear,
            boolean carryForward,
            int maxCarryForwardDays,
            boolean paid,
            boolean active) {}

    public record LeaveBalanceDto(
            Long leaveTypeId,
            String leaveCode,
            String leaveName,
            int year,
            boolean paid,
            BigDecimal entitled,
            BigDecimal carriedForward,
            BigDecimal used,
            BigDecimal available) {}

    public record LeaveApplicationDto(
            Long id,
            String applicationNumber,
            Long employeeId,
            String employeeCode,
            String employeeName,
            Long leaveTypeId,
            String leaveCode,
            LocalDate fromDate,
            LocalDate toDate,
            boolean halfDay,
            BigDecimal days,
            String reason,
            String status,
            Long decidedBy,
            Instant decidedAt,
            String decisionReason,
            Long version,
            List<String> allowedActions) {}

    /** employeeId is honoured only for callers who may act for others; everyone else applies for themselves. */
    public record ApplyLeaveRequest(
            Long employeeId,
            @NotNull Long leaveTypeId,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            boolean halfDay,
            @Size(max = 300) String reason) {}

    public record LeaveDecisionRequest(@Size(max = 300) String reason) {}
}
