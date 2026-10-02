package com.springmfg.ims.inventory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.inventory.domain.Alert;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    /** Find an open alert by its deduplication key. */
    @Query("SELECT a FROM Alert a WHERE a.openKey = :key AND a.resolvedAt IS NULL")
    Optional<Alert> findOpenByKey(@Param("key") String openKey);

    /** Count open alerts by type. */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.alertType = :type AND a.resolvedAt IS NULL")
    long countOpenByType(@Param("type") String alertType);

    /** Resolve all open LEDGER_MISMATCH alerts for an item. */
    @Modifying
    @Query("UPDATE Alert a SET a.resolvedAt = CURRENT_TIMESTAMP WHERE a.alertType = 'LEDGER_MISMATCH' AND a.entityId = :itemId AND a.resolvedAt IS NULL")
    void resolveOpenMismatchAlertsFor(@Param("itemId") Long itemId);
}
