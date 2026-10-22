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

public interface BinItemAssignmentRepository extends JpaRepository<BinItemAssignment, Long> {

    List<BinItemAssignment> findByBinId(Long binId);

    List<BinItemAssignment> findByMaterialId(Long materialId);

    List<BinItemAssignment> findByProductId(Long productId);

    @Query("SELECT a FROM BinItemAssignment a WHERE a.binId = :binId AND COALESCE(a.materialId, 0) = COALESCE(:materialId, 0)"
            + " AND COALESCE(a.productId, 0) = COALESCE(:productId, 0)")
    Optional<BinItemAssignment> findExisting(@Param("binId") Long binId, @Param("materialId") Long materialId,
                                             @Param("productId") Long productId);
}
