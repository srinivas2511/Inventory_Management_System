package com.mfg.ims.masterdata.machine;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MachineRepository extends JpaRepository<Machine, Long> {

    List<Machine> findAllByActiveTrue();

    Optional<Machine> findByMachineCode(String code);

    boolean existsByMachineCode(String code);
}
