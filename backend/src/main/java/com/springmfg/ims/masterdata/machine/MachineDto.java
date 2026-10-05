package com.springmfg.ims.masterdata.machine;

public record MachineDto(
        Long id,
        String code,
        String name,
        String machineType,
        String productionLine,
        String status,
        boolean active,
        Long costCentreId) {

    public static MachineDto from(Machine m) {
        return new MachineDto(
                m.getId(),
                m.getMachineCode(),
                m.getName(),
                m.getMachineType(),
                m.getProductionLine(),
                m.getStatus(),
                m.isActive(),
                m.getCostCentreId());
    }
}
