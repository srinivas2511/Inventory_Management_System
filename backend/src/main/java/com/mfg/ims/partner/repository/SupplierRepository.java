package com.mfg.ims.partner.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import com.mfg.ims.common.search.SearchText;
import com.mfg.ims.partner.domain.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsBySupplierCode(String supplierCode);

    Optional<Supplier> findBySupplierCode(String supplierCode);

    @Query("SELECT s FROM Supplier s WHERE " +
           "(:q = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(s.supplierCode) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:status IS NULL OR s.status = :status)")
    Page<Supplier> searchNormalised(@Param("q") String q,
                          @Param("status") String status,
                          Pageable pageable);

    default Page<Supplier> search(String q, String status, Pageable pageable) {
        return searchNormalised(SearchText.normalise(q), status, pageable);
    }
}
