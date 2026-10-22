package com.mfg.ims.hrm.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.hrm.domain.PayrollRun;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

    Optional<PayrollRun> findByPeriodYearAndPeriodMonth(short year, short month);

    boolean existsByPeriodYearAndPeriodMonthAndStatusIn(short year, short month, Collection<String> statuses);

    List<PayrollRun> findAllByOrderByPeriodYearDescPeriodMonthDesc();
}
