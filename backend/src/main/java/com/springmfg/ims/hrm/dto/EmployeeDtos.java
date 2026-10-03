package com.springmfg.ims.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.springmfg.ims.hrm.domain.EmploymentType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    /** Pay and bank fields are null unless the caller holds EMPLOYEE_MANAGE or PAYROLL_RUN. */
    public record EmployeeDto(
            Long id,
            String employeeCode,
            Long userId,
            String username,
            String firstName,
            String lastName,
            String fullName,
            String email,
            String phone,
            String designation,
            String department,
            Long plantId,
            Long costCentreId,
            LocalDate dateOfJoining,
            LocalDate dateOfLeaving,
            String employmentType,
            String payGrade,
            String defaultShift,
            boolean active,
            boolean compensationVisible,
            BigDecimal basicPay,
            BigDecimal fixedTravelAllowance,
            BigDecimal fixedOtherAllowances,
            String bankAccountNo,
            String bankIfsc,
            String pfNumber,
            String esiNumber,
            Long version) {}

    public record EmployeeSummaryDto(
            Long id,
            String employeeCode,
            String fullName,
            String designation,
            String department) {}

    public record SaveEmployeeRequest(
            @Size(max = 20) String employeeCode,
            Long userId,
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @Email @Size(max = 160) String email,
            @Size(max = 20) String phone,
            @Size(max = 80) String designation,
            @Size(max = 60) String department,
            Long plantId,
            Long costCentreId,
            @NotNull LocalDate dateOfJoining,
            @NotNull EmploymentType employmentType,
            @Size(max = 10) String payGrade,
            @PositiveOrZero BigDecimal basicPay,
            @PositiveOrZero BigDecimal fixedTravelAllowance,
            @PositiveOrZero BigDecimal fixedOtherAllowances,
            @Size(max = 30) String bankAccountNo,
            @Size(max = 15) String bankIfsc,
            @Size(max = 20) String pfNumber,
            @Size(max = 20) String esiNumber,
            @Size(max = 10) String defaultShift,
            @Size(max = 300) String payChangeReason,
            Long version) {}

    public record DeactivateEmployeeRequest(
            @NotBlank @Size(max = 300) String reason,
            LocalDate dateOfLeaving) {}

    public record UnlinkedUserDto(Long id, String username, String fullName, String roles) {}
}
