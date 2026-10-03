package com.springmfg.ims.hrm.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class PayrollDtos {

    private PayrollDtos() {
    }

    public record PayrollRunDto(
            Long id,
            String runNumber,
            int periodMonth,
            int periodYear,
            String status,
            Long initiatedBy,
            Instant completedAt,
            Long approvedBy,
            Integer totalEmployees,
            BigDecimal totalGross,
            BigDecimal totalNet,
            Long journalHeaderId,
            String journalNumber,
            Instant paidAt,
            Long paidBy,
            Long paymentJournalHeaderId,
            Long version,
            List<String> allowedActions) {}

    public record InitiatePayrollRequest(
            @NotNull @Min(1) @Max(12) Integer month,
            @NotNull @Min(2000) @Max(2100) Integer year) {}

    public record MarkPaidRequest(LocalDate paymentDate) {}

    public record PayrollSlipDto(
            Long id,
            Long payrollRunId,
            String runNumber,
            int periodMonth,
            int periodYear,
            Long employeeId,
            String employeeCode,
            String employeeName,
            String designation,
            String department,
            Long costCentreId,
            BigDecimal basicPay,
            BigDecimal hra,
            BigDecimal da,
            BigDecimal travelAllowance,
            BigDecimal otherAllowances,
            BigDecimal grossPay,
            BigDecimal pfEmployee,
            BigDecimal pfEmployer,
            BigDecimal esiEmployee,
            BigDecimal esiEmployer,
            BigDecimal professionalTax,
            BigDecimal tds,
            BigDecimal totalDeductions,
            BigDecimal netPay,
            BigDecimal workingDays,
            BigDecimal paidDays,
            BigDecimal lopDays) {}
}
