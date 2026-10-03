package com.springmfg.ims.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.hrm.domain.LeaveType;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    Optional<LeaveType> findByLeaveCode(String leaveCode);

    List<LeaveType> findByActiveTrueOrderByLeaveCodeAsc();
}
