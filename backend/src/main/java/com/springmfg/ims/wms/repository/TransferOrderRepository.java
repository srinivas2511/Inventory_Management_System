package com.springmfg.ims.wms.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.wms.domain.*;

public interface TransferOrderRepository extends JpaRepository<TransferOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransferOrder t WHERE t.id = :id")
    Optional<TransferOrder> lockById(@Param("id") Long id);

    @Query("""
            SELECT t FROM TransferOrder t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:movementType IS NULL OR t.movementType = :movementType)
              AND (:warehouseId IS NULL OR t.warehouseId = :warehouseId)
              AND (:assignedTo IS NULL OR t.assignedTo = :assignedTo)
              AND (:waveId IS NULL OR t.waveId = :waveId)
              AND (:q = '' OR LOWER(t.toNumber) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<TransferOrder> search(@Param("status") WmsEnums.TransferOrderStatus status,
                               @Param("movementType") WmsEnums.MovementType movementType,
                               @Param("warehouseId") Long warehouseId, @Param("assignedTo") Long assignedTo,
                               @Param("waveId") Long waveId, @Param("q") String q, Pageable pageable);

    List<TransferOrder> findBySourceDocumentTypeAndSourceDocumentId(String sourceDocumentType, Long sourceDocumentId);

    List<TransferOrder> findByWaveId(Long waveId);
}
