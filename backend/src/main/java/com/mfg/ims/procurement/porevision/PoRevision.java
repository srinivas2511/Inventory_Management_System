package com.mfg.ims.procurement.porevision;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "po_revisions", schema = "ims")
public class PoRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_id", nullable = false)
    private Long poId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getPoId() { return poId; }
    public void setPoId(Long v) { this.poId = v; }
    public int getRevisionNo() { return revisionNo; }
    public void setRevisionNo(int v) { this.revisionNo = v; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String v) { this.snapshot = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long v) { this.createdBy = v; }
    public Instant getCreatedAt() { return createdAt; }
}
