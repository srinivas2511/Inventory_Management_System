package com.mfg.ims.wms.repository;

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

import com.mfg.ims.wms.domain.*;

public interface CycleCountRepository extends JpaRepository<CycleCount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CycleCount c WHERE c.id = :id")
    Optional<CycleCount> lockById(@Param("id") Long id);

    @Query("""
            SELECT c FROM CycleCount c
            WHERE (:status IS NULL OR c.status = :status)
              AND (:warehouseId IS NULL OR c.warehouseId = :warehouseId)
            """)
    Page<CycleCount> search(@Param("status") WmsEnums.CycleCountStatus status,
                            @Param("warehouseId") Long warehouseId, Pageable pageable);
}
