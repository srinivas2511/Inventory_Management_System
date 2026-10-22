package com.mfg.ims.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.hrm.domain.Shift;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    Optional<Shift> findByShiftCode(String shiftCode);

    List<Shift> findAllByOrderByShiftCodeAsc();
}
