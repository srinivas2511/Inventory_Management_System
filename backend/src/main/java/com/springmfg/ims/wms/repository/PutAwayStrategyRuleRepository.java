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

public interface PutAwayStrategyRuleRepository extends JpaRepository<PutAwayStrategyRule, Long> {

    List<PutAwayStrategyRule> findByWarehouseIdOrderByMaterialType(Long warehouseId);

    Optional<PutAwayStrategyRule> findByWarehouseIdAndMaterialType(Long warehouseId, String materialType);

    Optional<PutAwayStrategyRule> findByWarehouseIdAndMaterialTypeIsNull(Long warehouseId);
}
