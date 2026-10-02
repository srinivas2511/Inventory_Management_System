package com.springmfg.ims.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.inventory.domain.FgBatch;

public interface FgBatchRepository extends JpaRepository<FgBatch, Long> {

    Optional<FgBatch> findByBatchNumber(String batchNumber);

    boolean existsByBatchNumber(String batchNumber);

    Optional<FgBatch> findByProductionOrderId(Long productionOrderId);

    List<FgBatch> findByQualityStatus(String qualityStatus);

    @Query("SELECT fb FROM FgBatch fb WHERE (:status IS NULL OR fb.qualityStatus = :status) " +
           "AND (:productId IS NULL OR fb.product.id = :productId)")
    Page<FgBatch> search(@Param("status") String status, @Param("productId") Long productId, Pageable pageable);
}
