package com.springmfg.ims.inventory.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.inventory.domain.StockAdjustment;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    Optional<StockAdjustment> findByAdjustmentNumber(String number);

    @Query("SELECT a FROM StockAdjustment a WHERE (:status IS NULL OR a.status = :status) AND (:wh IS NULL OR a.warehouseId = :wh) ORDER BY a.createdAt DESC")
    Page<StockAdjustment> search(@Param("status") String status, @Param("wh") Long warehouseId, Pageable pageable);
}
