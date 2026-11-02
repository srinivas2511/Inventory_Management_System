package com.mfg.ims.hrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class OperatorDtos {

    private OperatorDtos() {}

    public record OperatorListDto(
            Long id,
            String operatorCode,
            String employeeCode,
            String fullName,
            String designation,
            String department,
            boolean active,
            Long version) {}

    public record SaveOperatorRequest(
            @NotBlank @Size(max = 20) String operatorCode,
            @NotBlank @Size(max = 120) String fullName,
            boolean active) {}
}
