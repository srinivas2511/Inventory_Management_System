package com.mfg.ims.engineering.bom;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BomRepository extends JpaRepository<Bom, Long> {

    Optional<Bom> findByProductIdAndStatus(Long productId, String status);

    Page<Bom> findByProductId(Long productId, Pageable pageable);

    List<Bom> findByProductIdAndStatusIn(Long productId, List<String> statuses);

    @Query("SELECT b FROM Bom b WHERE (:productId IS NULL OR b.productId = :productId) " +
           "AND (:status IS NULL OR b.status = :status)")
    Page<Bom> search(@Param("productId") Long productId, @Param("status") String status, Pageable pageable);

    @Query("SELECT MAX(b.revision) FROM Bom b WHERE b.bomNumber = :bomNumber")
    Optional<String> findMaxRevisionByBomNumber(@Param("bomNumber") String bomNumber);

    boolean existsByProductIdAndStatus(Long productId, String status);
}
