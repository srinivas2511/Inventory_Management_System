package com.mfg.ims.integration.webhook.domain;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.mfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "webhook_deliveries", schema = "ims")
public class WebhookDelivery extends BaseEntity {

    public static final String PENDING = "PENDING";
    public static final String RETRY = "RETRY";
    public static final String DELIVERED = "DELIVERED";
    public static final String FAILED = "FAILED";

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private Long subscriptionId;

    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private String eventType;

    @Column(name = "event_key", nullable = false, length = 80, updatable = false)
    private String eventKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> payload;

    @Column(nullable = false, length = 10)
    private String status = PENDING;

    @Column(name = "retry_count", nullable = false)
    private short retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "attempted_at")
    private Instant attemptedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", length = 2000)
    private String responseBody;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public Long getSubscriptionId() { return subscriptionId; }
    public String getEventType() { return eventType; }
    public String getEventKey() { return eventKey; }
    public Map<String, Object> getPayload() { return payload; }
    public String getStatus() { return status; }
    public short getRetryCount() { return retryCount; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public String getLastError() { return lastError; }

    public void setSubscriptionId(Long v) { this.subscriptionId = v; }
    public void setEventType(String v) { this.eventType = v; }
    public void setEventKey(String v) { this.eventKey = v; }
    public void setPayload(Map<String, Object> v) { this.payload = v; }
    public void setStatus(String v) { this.status = v; }
    public void setRetryCount(short v) { this.retryCount = v; }
    public void setNextRetryAt(Instant v) { this.nextRetryAt = v; }
    public void setAttemptedAt(Instant v) { this.attemptedAt = v; }
    public void setDeliveredAt(Instant v) { this.deliveredAt = v; }
    public void setResponseStatus(Integer v) { this.responseStatus = v; }
    public void setResponseBody(String v) { this.responseBody = v; }
    public void setLastError(String v) { this.lastError = v; }
}
