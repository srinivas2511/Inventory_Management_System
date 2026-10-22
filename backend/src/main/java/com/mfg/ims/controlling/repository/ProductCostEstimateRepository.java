package com.mfg.ims.controlling.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.controlling.domain.ProductCostEstimate;

public interface ProductCostEstimateRepository extends JpaRepository<ProductCostEstimate, Long> {

    Optional<ProductCostEstimate> findByProductIdAndStatus(Long productId, String status);

    @Query("SELECT e FROM ProductCostEstimate e WHERE (:productId IS NULL OR e.productId = :productId) "
            + "AND (:status IS NULL OR e.status = :status)")
    Page<ProductCostEstimate> search(@Param("productId") Long productId,
                                     @Param("status") String status,
                                     Pageable pageable);
}
