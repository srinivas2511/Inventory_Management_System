package com.mfg.ims.maintenance.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.maintenance.domain.SparePartIssueLine;

public interface SparePartIssueLineRepository extends JpaRepository<SparePartIssueLine, Long> {

    List<SparePartIssueLine> findByMaintenanceRequestIdOrderByIdAsc(Long maintenanceRequestId);

    @Query("SELECT COALESCE(SUM(l.issuedQty * COALESCE(l.unitCost, 0)), 0) FROM SparePartIssueLine l "
            + "WHERE l.maintenanceRequestId = :requestId")
    BigDecimal totalCost(@Param("requestId") Long requestId);
}
