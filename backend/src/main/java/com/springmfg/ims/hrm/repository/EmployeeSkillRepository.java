package com.springmfg.ims.hrm.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.hrm.domain.EmployeeSkill;

public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, Long> {

    List<EmployeeSkill> findByEmployeeIdOrderByIdAsc(Long employeeId);

    @Query("select distinct s.employeeId from EmployeeSkill s where s.active = true and s.machineId = :machineId "
            + "and (s.expiresOn is null or s.expiresOn >= :today)")
    List<Long> authorisedForMachine(@Param("machineId") Long machineId, @Param("today") LocalDate today);

    @Query("select distinct s.employeeId from EmployeeSkill s where s.active = true and s.operationId = :operationId "
            + "and (s.expiresOn is null or s.expiresOn >= :today)")
    List<Long> authorisedForOperation(@Param("operationId") Long operationId, @Param("today") LocalDate today);
}
