package com.springmfg.ims.integration.webhook.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.integration.webhook.domain.WebhookDelivery;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    boolean existsBySubscriptionIdAndEventKey(Long subscriptionId, String eventKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM WebhookDelivery d WHERE d.id = :id")
    Optional<WebhookDelivery> findByIdForUpdate(@Param("id") Long id);

    /** Ids of deliveries that are due: never tried or scheduled for a retry at or before {@code now}. */
    @Query("""
            SELECT d.id FROM WebhookDelivery d
            WHERE d.status IN ('PENDING','RETRY') AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now)
            ORDER BY d.createdAt
            """)
    List<Long> findDueIds(@Param("now") Instant now, Pageable pageable);

    /** {@code subscriptionId} 0 and {@code status} "" mean no filter (a typed value, never a null parameter). */
    @Query("""
            SELECT d FROM WebhookDelivery d
            WHERE (:subscriptionId = 0 OR d.subscriptionId = :subscriptionId)
              AND (:status = '' OR d.status = :status)
            """)
    Page<WebhookDelivery> search(@Param("subscriptionId") long subscriptionId, @Param("status") String status,
                                 Pageable pageable);

    long countByStatusIn(List<String> statuses);
}
