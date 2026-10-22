package com.mfg.ims.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.inventory.domain.Alert;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query("SELECT a FROM Alert a WHERE a.openKey = :key AND a.resolvedAt IS NULL")
    Optional<Alert> findOpenByKey(@Param("key") String openKey);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.alertType = :type AND a.resolvedAt IS NULL")
    long countOpenByType(@Param("type") String alertType);

    @Modifying
    @Query("UPDATE Alert a SET a.resolvedAt = CURRENT_TIMESTAMP, a.status = 'RESOLVED' WHERE a.alertType = 'LEDGER_MISMATCH' AND a.entityId = :itemId AND a.resolvedAt IS NULL")
    void resolveOpenMismatchAlertsFor(@Param("itemId") Long itemId);

    @Query("SELECT a FROM Alert a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:alertType IS NULL OR a.alertType = :alertType) " +
           "ORDER BY a.firstSeenAt DESC")
    Page<Alert> search(@Param("status") String status,
                       @Param("severity") String severity,
                       @Param("alertType") String alertType,
                       Pageable pageable);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.severity = :sev AND a.resolvedAt IS NULL")
    long countBySeverityOpen(@Param("sev") String severity);

    @Query("SELECT a FROM Alert a WHERE a.alertType = :type AND a.entityType = :et AND a.entityId = :eid AND a.resolvedAt IS NULL")
    List<Alert> findOpenByTypeAndEntity(@Param("type") String alertType,
                                        @Param("et") String entityType,
                                        @Param("eid") Long entityId);
}
