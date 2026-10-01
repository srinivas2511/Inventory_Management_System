package com.springmfg.ims.common.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Standard columns for every non-ledger table (DESIGN.md section 1.1).
 * <p>
 * The inventory ledger and audit log do <b>not</b> extend this class: they are append-only and have their
 * own immutable shape.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    /** Optimistic lock. A stale write surfaces as HTTP 409 VERSION_CONFLICT. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Reserved for future multi-company support (ARCHITECTURE.md section 4.4). */
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId = 1L;

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Long getVersion() {
        return version;
    }

    public Long getCompanyId() {
        return companyId;
    }
}
