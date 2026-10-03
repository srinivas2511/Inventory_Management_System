package com.springmfg.ims.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.hrm.domain.LeaveBalance;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndBalanceYear(Long employeeId, Long leaveTypeId, int year);

    List<LeaveBalance> findByEmployeeIdAndBalanceYear(Long employeeId, int year);
}
