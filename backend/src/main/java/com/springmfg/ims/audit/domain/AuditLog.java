package com.springmfg.ims.audit.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs", schema = "ims")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant occurredAt = Instant.now();

    private Long userId;

    @Column(length = 50)
    private String username;

    @Column(length = 200)
    private String roles;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(nullable = false, length = 60)
    private String entity;

    @Column(length = 60)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    private String newValue;

    @Column(length = 500)
    private String reason;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 40)
    private String correlationId;

    protected AuditLog() {}

    public AuditLog(Long userId, String username, String roles, String action,
                    String entity, String entityId, String oldValue, String newValue,
                    String reason, String ipAddress, String correlationId) {
        this.userId = userId;
        this.username = username;
        this.roles = roles;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
        this.ipAddress = ipAddress;
        this.correlationId = correlationId;
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getRoles() { return roles; }
    public String getAction() { return action; }
    public String getEntity() { return entity; }
    public String getEntityId() { return entityId; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getReason() { return reason; }
    public String getIpAddress() { return ipAddress; }
    public String getCorrelationId() { return correlationId; }
}
