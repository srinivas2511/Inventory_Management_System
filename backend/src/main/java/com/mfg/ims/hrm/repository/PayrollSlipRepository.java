package com.mfg.ims.hrm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.hrm.domain.PayrollSlip;

public interface PayrollSlipRepository extends JpaRepository<PayrollSlip, Long> {

    List<PayrollSlip> findByPayrollRunIdOrderByEmployeeIdAsc(Long payrollRunId);

    void deleteByPayrollRunId(Long payrollRunId);
}
