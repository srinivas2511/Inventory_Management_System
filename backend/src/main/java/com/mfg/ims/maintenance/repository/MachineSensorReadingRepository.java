package com.mfg.ims.maintenance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.maintenance.domain.MachineSensorReading;

public interface MachineSensorReadingRepository extends JpaRepository<MachineSensorReading, Long> {

    List<MachineSensorReading> findTop50ByMachineIdOrderByReadAtDesc(Long machineId);
}
