package com.springmfg.ims.masterdata.operation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationRepository extends JpaRepository<Operation, Long> {

    List<Operation> findAllByActiveTrue();

    Optional<Operation> findByOperationCode(String code);

    boolean existsByOperationCode(String code);
}
