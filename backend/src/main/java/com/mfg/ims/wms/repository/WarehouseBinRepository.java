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

public interface WarehouseBinRepository extends JpaRepository<WarehouseBin, Long> {

    @Query("""
            SELECT b FROM WarehouseBin b, WarehouseZone z
            WHERE z.id = b.zoneId AND z.warehouseId = :warehouseId
              AND (:zoneId IS NULL OR b.zoneId = :zoneId)
              AND (:active IS NULL OR b.active = :active)
              AND (:q = '' OR LOWER(b.binCode) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<WarehouseBin> search(@Param("warehouseId") Long warehouseId, @Param("zoneId") Long zoneId,
                              @Param("active") Boolean active, @Param("q") String q, Pageable pageable);

    @Query("""
            SELECT b FROM WarehouseBin b, WarehouseZone z
            WHERE z.id = b.zoneId AND z.warehouseId = :warehouseId AND b.active = TRUE AND z.active = TRUE
            ORDER BY z.zoneCode, b.binCode
            """)
    List<WarehouseBin> findActiveByWarehouse(@Param("warehouseId") Long warehouseId);

    List<WarehouseBin> findByZoneIdOrderByBinCode(Long zoneId);

    Optional<WarehouseBin> findByLocationId(Long locationId);

    List<WarehouseBin> findByLocationIdIn(Collection<Long> locationIds);

    List<WarehouseBin> findByIdIn(Collection<Long> ids);

    @Query("SELECT COUNT(b) FROM WarehouseBin b WHERE b.zoneId = :zoneId AND b.active = TRUE")
    long countActiveInZone(@Param("zoneId") Long zoneId);
}
