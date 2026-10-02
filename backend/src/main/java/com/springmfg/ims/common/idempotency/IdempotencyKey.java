package com.springmfg.ims.common.idempotency;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_keys", schema = "ims")
public class IdempotencyKey {

    @Id
    @Column(length = 80)
    private String key;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected IdempotencyKey() {}

    public IdempotencyKey(String key, Long userId, String requestHash) {
        this.key = key;
        this.userId = userId;
        this.requestHash = requestHash;
    }

    public String getKey() { return key; }
    public Long getUserId() { return userId; }
    public String getRequestHash() { return requestHash; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public Instant getCreatedAt() { return createdAt; }

    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
}
