package com.springmfg.ims.partner.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.partner.domain.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsBySupplierCode(String supplierCode);

    @Query("SELECT s FROM Supplier s WHERE " +
           "(:q IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(s.supplierCode) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:status IS NULL OR s.status = :status)")
    Page<Supplier> search(@Param("q") String q,
                          @Param("status") String status,
                          Pageable pageable);
}
