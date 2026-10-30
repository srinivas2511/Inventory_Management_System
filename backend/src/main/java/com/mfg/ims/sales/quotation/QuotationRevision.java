package com.mfg.ims.sales.quotation;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quotation_revisions", schema = "ims")
public class QuotationRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber = 1;

    @Column(name = "revised_at", nullable = false)
    private Instant revisedAt = Instant.now();

    @Column(name = "revised_by")
    private Long revisedBy;

    @Column(name = "reason")
    private String reason;

    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    public Long getId() { return id; }
    public Quotation getQuotation() { return quotation; }
    public void setQuotation(Quotation v) { this.quotation = v; }
    public int getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(int v) { this.revisionNumber = v; }
    public Instant getRevisedAt() { return revisedAt; }
    public void setRevisedAt(Instant v) { this.revisedAt = v; }
    public Long getRevisedBy() { return revisedBy; }
    public void setRevisedBy(Long v) { this.revisedBy = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String v) { this.snapshot = v; }
}
