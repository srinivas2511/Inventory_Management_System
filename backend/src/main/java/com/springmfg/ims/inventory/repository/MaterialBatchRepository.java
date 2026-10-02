package com.springmfg.ims.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.inventory.domain.MaterialBatch;

public interface MaterialBatchRepository extends JpaRepository<MaterialBatch, Long> {

    Optional<MaterialBatch> findByBatchNumber(String batchNumber);

    boolean existsByBatchNumber(String batchNumber);

    List<MaterialBatch> findByMaterialIdAndStatus(Long materialId, String status);

    @Query("SELECT b FROM MaterialBatch b WHERE b.material.id = :matId AND b.status = 'AVAILABLE' " +
           "ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END, b.expiryDate ASC, b.receivedDate ASC, b.id ASC")
    List<MaterialBatch> findAvailableByMaterialFefo(@Param("matId") Long materialId);

    @Query("SELECT b FROM MaterialBatch b WHERE (:matId IS NULL OR b.material.id = :matId) AND (:status IS NULL OR b.status = :status) ORDER BY b.receivedDate DESC")
    Page<MaterialBatch> search(@Param("matId") Long materialId, @Param("status") String status, Pageable pageable);
}
