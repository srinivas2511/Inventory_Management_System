package com.springmfg.ims.audit.repository;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.audit.domain.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) " +
           "AND (:entity IS NULL OR a.entity = :entity) " +
           "AND (:action IS NULL OR a.action = :action) " +
           "AND (:from IS NULL OR a.occurredAt >= :from) " +
           "AND (:to IS NULL OR a.occurredAt <= :to) " +
           "ORDER BY a.occurredAt DESC")
    Page<AuditLog> search(@Param("userId") Long userId,
                          @Param("entity") String entity,
                          @Param("action") String action,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
