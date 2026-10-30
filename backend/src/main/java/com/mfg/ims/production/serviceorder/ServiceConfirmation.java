package com.mfg.ims.production.serviceorder;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "service_order_confirmations", schema = "ims")
public class ServiceConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "so_id", nullable = false)
    private Long soId;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt = Instant.now();

    @Column(name = "confirmed_by", nullable = false)
    private Long confirmedBy;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public Long getId() { return id; }
    public Long getSoId() { return soId; }
    public void setSoId(Long soId) { this.soId = soId; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
