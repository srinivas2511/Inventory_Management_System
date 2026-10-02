package com.springmfg.ims.warehouse.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.warehouse.domain.WarehouseLocation;

public interface WarehouseLocationRepository extends JpaRepository<WarehouseLocation, Long> {

    List<WarehouseLocation> findByWarehouseIdAndActiveTrue(Long warehouseId);

    List<WarehouseLocation> findByWarehouseId(Long warehouseId);

    boolean existsByWarehouseIdAndLocationCode(Long warehouseId, String locationCode);

    Optional<WarehouseLocation> findByWarehouseIdAndLocationCode(Long warehouseId, String locationCode);

    @Query("SELECT COUNT(l) FROM WarehouseLocation l WHERE l.warehouse.id = :warehouseId AND l.locationType = :type AND l.active = true")
    long countByWarehouseIdAndLocationType(@Param("warehouseId") Long warehouseId, @Param("type") String locationType);

    @Query("SELECT l.locationType FROM WarehouseLocation l WHERE l.warehouse.id = :warehouseId AND l.active = true GROUP BY l.locationType")
    List<String> findActiveLocationTypesByWarehouse(@Param("warehouseId") Long warehouseId);

    @Query("SELECT l FROM WarehouseLocation l WHERE l.warehouse.id = :warehouseId AND l.locationType = :type AND l.active = true")
    List<WarehouseLocation> findByWarehouseIdAndLocationType(@Param("warehouseId") Long warehouseId, @Param("type") String type);
}
