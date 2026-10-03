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

public interface PickWaveRepository extends JpaRepository<PickWave, Long> {

    @Query("""
            SELECT w FROM PickWave w
            WHERE (:status IS NULL OR w.status = :status)
              AND (:warehouseId IS NULL OR w.warehouseId = :warehouseId)
            """)
    Page<PickWave> search(@Param("status") WmsEnums.WaveStatus status, @Param("warehouseId") Long warehouseId,
                          Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM PickWave w WHERE w.id = :id")
    Optional<PickWave> lockById(@Param("id") Long id);
}
