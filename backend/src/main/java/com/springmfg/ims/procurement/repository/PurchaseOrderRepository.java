package com.springmfg.ims.procurement.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.procurement.domain.PurchaseOrder;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    Page<PurchaseOrder> findBySupplierId(Long supplierId, Pageable pageable);

    Page<PurchaseOrder> findByStatus(String status, Pageable pageable);

    @Query("SELECT p FROM PurchaseOrder p WHERE (:status IS NULL OR p.status = :status) " +
           "AND (:supplierId IS NULL OR p.supplierId = :supplierId)")
    Page<PurchaseOrder> search(@Param("status") String status,
                               @Param("supplierId") Long supplierId,
                               Pageable pageable);
}
