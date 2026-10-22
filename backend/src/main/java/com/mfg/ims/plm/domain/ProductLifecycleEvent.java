package com.mfg.ims.plm.domain;

import java.time.Instant;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One row per lifecycle change, or per ECO implementation touching the product (then from and to are equal). */
@Entity
@Table(name = "product_lifecycle_events", schema = "ims")
public class ProductLifecycleEvent extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "from_status", length = 15)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 15)
    private String toStatus;

    @Column(name = "eco_id")
    private Long ecoId;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String v) { this.fromStatus = v; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String v) { this.toStatus = v; }
    public Long getEcoId() { return ecoId; }
    public void setEcoId(Long v) { this.ecoId = v; }
    public Long getChangedBy() { return changedBy; }
    public void setChangedBy(Long v) { this.changedBy = v; }
    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant v) { this.changedAt = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
}
