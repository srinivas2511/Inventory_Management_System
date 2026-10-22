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

public interface WarehouseZoneRepository extends JpaRepository<WarehouseZone, Long> {

    List<WarehouseZone> findByWarehouseIdOrderByZoneCode(Long warehouseId);

    Optional<WarehouseZone> findByWarehouseIdAndZoneCode(Long warehouseId, String zoneCode);

    List<WarehouseZone> findByIdIn(Collection<Long> ids);
}
