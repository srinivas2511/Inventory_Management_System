package com.springmfg.ims.hrm.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.hrm.domain.TrainingRecord;

public interface TrainingRecordRepository extends JpaRepository<TrainingRecord, Long> {

    List<TrainingRecord> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

    @Query("select t from TrainingRecord t where t.status = 'COMPLETED' and t.renewalDueOn is not null "
            + "and t.renewalDueOn <= :until order by t.renewalDueOn")
    List<TrainingRecord> findRenewalsDueBy(@Param("until") LocalDate until);
}
