package com.springmfg.ims.procurement.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.procurement.domain.PurchaseOrderItem;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {

    List<PurchaseOrderItem> findByPurchaseOrderId(Long poId);

    long countByMaterialId(Long materialId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM PurchaseOrderItem i WHERE i.id = :id")
    Optional<PurchaseOrderItem> lockById(@Param("id") Long id);
}
