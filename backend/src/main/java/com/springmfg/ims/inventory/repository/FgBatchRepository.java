package com.springmfg.ims.inventory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.inventory.domain.FgBatch;

public interface FgBatchRepository extends JpaRepository<FgBatch, Long> {

    Optional<FgBatch> findByBatchNumber(String batchNumber);

    boolean existsByBatchNumber(String batchNumber);
}
