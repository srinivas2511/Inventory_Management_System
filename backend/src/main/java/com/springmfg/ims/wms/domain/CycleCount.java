package com.springmfg.ims.wms.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "cycle_counts", schema = "ims")
public class CycleCount extends BaseEntity {

    @Column(name = "count_number", nullable = false, length = 20)
    private String countNumber;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "count_type", nullable = false, length = 10)
    private WmsEnums.CycleCountType countType;

    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "abc_class", length = 1)
    private String abcClass;

    @Column(name = "sample_size")
    private Integer sampleSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private WmsEnums.CycleCountStatus status = WmsEnums.CycleCountStatus.CREATED;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "notes", length = 300)
    private String notes;

    public CycleCount() {
    }

    public String getCountNumber() { return countNumber; }
    public void setCountNumber(String countNumber) { this.countNumber = countNumber; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public WmsEnums.CycleCountType getCountType() { return countType; }
    public void setCountType(WmsEnums.CycleCountType countType) { this.countType = countType; }
    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
    public String getAbcClass() { return abcClass; }
    public void setAbcClass(String abcClass) { this.abcClass = abcClass; }
    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer sampleSize) { this.sampleSize = sampleSize; }
    public WmsEnums.CycleCountStatus getStatus() { return status; }
    public void setStatus(WmsEnums.CycleCountStatus status) { this.status = status; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
