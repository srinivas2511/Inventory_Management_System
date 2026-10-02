package com.springmfg.ims.masterdata.operation;

import java.math.BigDecimal;

public record OperationDto(
        Long id,
        String code,
        String name,
        String description,
        String defaultMachineType,
        BigDecimal standardTimeMin,
        BigDecimal setupTimeMin,
        boolean isInspection,
        boolean active) {

    public static OperationDto from(Operation o) {
        return new OperationDto(
                o.getId(),
                o.getOperationCode(),
                o.getName(),
                o.getDescription(),
                o.getDefaultMachineType(),
                o.getStandardTimeMin(),
                o.getSetupTimeMin(),
                o.isInspection(),
                o.isActive());
    }
}
