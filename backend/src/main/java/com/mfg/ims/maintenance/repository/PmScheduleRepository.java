package com.mfg.ims.maintenance.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.maintenance.domain.PmSchedule;

public interface PmScheduleRepository extends JpaRepository<PmSchedule, Long> {

    @Query("SELECT s FROM PmSchedule s WHERE s.active = true AND s.nextDueDate <= :cutoff")
    List<PmSchedule> findDue(@Param("cutoff") LocalDate cutoff);

    List<PmSchedule> findByMachineId(Long machineId);

    List<PmSchedule> findByActiveTrueOrderByNextDueDateAsc();
}
