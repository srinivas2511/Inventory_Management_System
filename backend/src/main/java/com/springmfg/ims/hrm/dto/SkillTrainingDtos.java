package com.springmfg.ims.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.springmfg.ims.hrm.domain.SkillType;
import com.springmfg.ims.hrm.domain.TrainingStatus;
import com.springmfg.ims.hrm.domain.TrainingType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class SkillTrainingDtos {

    private SkillTrainingDtos() {
    }

    public record SkillDto(
            Long id,
            Long employeeId,
            String skillType,
            Long machineId,
            String machineName,
            Long operationId,
            String operationName,
            String skillName,
            String certificationNo,
            Integer proficiencyLevel,
            LocalDate certifiedOn,
            LocalDate expiresOn,
            boolean expired,
            boolean active) {}

    public record SaveSkillRequest(
            @NotNull SkillType skillType,
            Long machineId,
            Long operationId,
            @Size(max = 80) String skillName,
            @Size(max = 40) String certificationNo,
            @Min(1) @Max(5) Integer proficiencyLevel,
            LocalDate certifiedOn,
            LocalDate expiresOn,
            Boolean active) {}

    public record TrainingDto(
            Long id,
            Long employeeId,
            String employeeCode,
            String employeeName,
            String programmeName,
            String trainingType,
            String provider,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal hours,
            String status,
            BigDecimal score,
            String certificateNo,
            LocalDate certifiedOn,
            LocalDate renewalDueOn,
            String notes,
            Long version) {}

    public record SaveTrainingRequest(
            @NotBlank @Size(max = 150) String programmeName,
            @NotNull TrainingType trainingType,
            @Size(max = 120) String provider,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @PositiveOrZero BigDecimal hours,
            @NotNull TrainingStatus status,
            @PositiveOrZero BigDecimal score,
            @Size(max = 40) String certificateNo,
            LocalDate certifiedOn,
            LocalDate renewalDueOn,
            @Size(max = 300) String notes,
            Long version) {}
}
