package com.mfg.ims.wms.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "pick_waves", schema = "ims")
public class PickWave extends BaseEntity {

    @Column(name = "wave_number", nullable = false, length = 20)
    private String waveNumber;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private WmsEnums.WaveStatus status = WmsEnums.WaveStatus.CREATED;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_by")
    private Long releasedBy;

    @Column(name = "notes", length = 300)
    private String notes;

    public PickWave() {
    }

    public String getWaveNumber() { return waveNumber; }
    public void setWaveNumber(String waveNumber) { this.waveNumber = waveNumber; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public WmsEnums.WaveStatus getStatus() { return status; }
    public void setStatus(WmsEnums.WaveStatus status) { this.status = status; }
    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
    public Long getReleasedBy() { return releasedBy; }
    public void setReleasedBy(Long releasedBy) { this.releasedBy = releasedBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
