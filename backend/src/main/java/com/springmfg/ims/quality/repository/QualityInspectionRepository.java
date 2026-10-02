package com.springmfg.ims.quality.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.quality.domain.QualityInspection;

public interface QualityInspectionRepository extends JpaRepository<QualityInspection, Long> {

    Optional<QualityInspection> findByInspectionNumber(String inspectionNumber);

    Page<QualityInspection> findByInspectionTypeAndStatus(String type, String status, Pageable pageable);

    Optional<QualityInspection> findByGoodsReceiptItemId(Long grItemId);

    Optional<QualityInspection> findByMaterialBatchId(Long batchId);

    @Query("SELECT q FROM QualityInspection q WHERE q.inspectionType = 'INCOMING' " +
           "AND q.materialBatchId = :batchId AND q.status NOT IN ('APPROVED','REJECTED')")
    Optional<QualityInspection> findOpenIncomingByBatch(@Param("batchId") Long batchId);

    @Query("SELECT q FROM QualityInspection q WHERE q.inspectionType = 'FINAL' " +
           "AND q.fgBatchId = :fgBatchId AND q.status NOT IN ('PASSED','FAILED','HOLD')")
    Optional<QualityInspection> findOpenFinalByFgBatch(@Param("fgBatchId") Long fgBatchId);

    @Query("SELECT q FROM QualityInspection q WHERE (:type IS NULL OR q.inspectionType = :type) " +
           "AND (:status IS NULL OR q.status = :status)")
    Page<QualityInspection> search(@Param("type") String type, @Param("status") String status,
                                   Pageable pageable);

    @Query("SELECT q.id FROM QualityInspection q WHERE q.status = 'PENDING' AND q.createdAt < :cutoff")
    List<Long> findIdsPendingBefore(@Param("cutoff") Instant cutoff);

    @Query("SELECT COUNT(q) FROM QualityInspection q WHERE q.status = 'PENDING'")
    long countPending();

    @Query("SELECT CASE WHEN COUNT(q) = 0 THEN CAST(0.0 AS java.math.BigDecimal) " +
           "ELSE CAST((SUM(CASE WHEN q.status IN ('PASSED','APPROVED') THEN 1.0 ELSE 0.0 END) / COUNT(q)) * 100 AS java.math.BigDecimal) END " +
           "FROM QualityInspection q WHERE q.status IN ('PASSED','APPROVED','FAILED','REJECTED') " +
           "AND FUNCTION('MONTH', q.createdAt) = FUNCTION('MONTH', CURRENT_DATE) " +
           "AND FUNCTION('YEAR', q.createdAt) = FUNCTION('YEAR', CURRENT_DATE)")
    java.math.BigDecimal passRateThisMonth();
}
