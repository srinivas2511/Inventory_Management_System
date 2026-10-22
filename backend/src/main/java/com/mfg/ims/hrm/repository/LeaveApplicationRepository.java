package com.mfg.ims.hrm.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.hrm.domain.LeaveApplication;

public interface LeaveApplicationRepository
        extends JpaRepository<LeaveApplication, Long>, JpaSpecificationExecutor<LeaveApplication> {

    @Query("select a from LeaveApplication a where a.employeeId = :employeeId and a.status in ('PENDING','APPROVED') "
            + "and a.fromDate <= :to and a.toDate >= :from")
    List<LeaveApplication> findOpenOverlapping(@Param("employeeId") Long employeeId, @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    @Query("select a from LeaveApplication a where a.employeeId = :employeeId and a.status = 'APPROVED' "
            + "and a.fromDate <= :to and a.toDate >= :from")
    List<LeaveApplication> findApprovedOverlapping(@Param("employeeId") Long employeeId,
                                                   @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select a from LeaveApplication a where a.status = 'APPROVED' and a.fromDate <= :to and a.toDate >= :from")
    List<LeaveApplication> findAllApprovedOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
