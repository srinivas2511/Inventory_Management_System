package com.mfg.ims.doccore.docseries;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_series_counters", schema = "ims")
public class DocumentSeriesCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "series_id", nullable = false)
    private Long seriesId;

    @Column(name = "period_key", nullable = false, length = 10)
    private String periodKey;

    @Column(name = "last_value", nullable = false)
    private long lastValue = 0;

    public DocumentSeriesCounter() {}

    public Long getId() { return id; }
    public Long getSeriesId() { return seriesId; }
    public void setSeriesId(Long v) { this.seriesId = v; }
    public String getPeriodKey() { return periodKey; }
    public void setPeriodKey(String v) { this.periodKey = v; }
    public long getLastValue() { return lastValue; }
    public void setLastValue(long v) { this.lastValue = v; }
}
