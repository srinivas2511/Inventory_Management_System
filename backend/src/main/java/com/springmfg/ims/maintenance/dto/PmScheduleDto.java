package com.springmfg.ims.maintenance.dto;

import java.time.LocalDate;

import com.springmfg.ims.maintenance.domain.PmSchedule;

public record PmScheduleDto(
        Long id,
        Long machineId,
        String taskName,
        int frequencyDays,
        LocalDate lastPerformedDate,
        LocalDate nextDueDate,
        Long assignedTo,
        boolean active) {

    public static PmScheduleDto from(PmSchedule s) {
        return new PmScheduleDto(
                s.getId(), s.getMachineId(), s.getTaskName(),
                s.getFrequencyDays(), s.getLastPerformedDate(), s.getNextDueDate(),
                s.getAssignedTo(), s.isActive());
    }
}
