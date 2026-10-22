package com.mfg.ims.wms.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "transfer_orders", schema = "ims")
public class TransferOrder extends BaseEntity {

    @Column(name = "to_number", nullable = false, length = 20)
    private String toNumber;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 18)
    private WmsEnums.MovementType movementType;

    @Column(name = "source_document_type", length = 20)
    private String sourceDocumentType;

    @Column(name = "source_document_id")
    private Long sourceDocumentId;

    @Column(name = "cross_dock_target_type", length = 20)
    private String crossDockTargetType;

    @Column(name = "cross_dock_target_id")
    private Long crossDockTargetId;

    @Column(name = "wave_id")
    private Long waveId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private WmsEnums.TransferOrderStatus status = WmsEnums.TransferOrderStatus.CREATED;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "notes", length = 500)
    private String notes;

    public TransferOrder() {
    }

    public String getToNumber() { return toNumber; }
    public void setToNumber(String toNumber) { this.toNumber = toNumber; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public WmsEnums.MovementType getMovementType() { return movementType; }
    public void setMovementType(WmsEnums.MovementType movementType) { this.movementType = movementType; }
    public String getSourceDocumentType() { return sourceDocumentType; }
    public void setSourceDocumentType(String sourceDocumentType) { this.sourceDocumentType = sourceDocumentType; }
    public Long getSourceDocumentId() { return sourceDocumentId; }
    public void setSourceDocumentId(Long sourceDocumentId) { this.sourceDocumentId = sourceDocumentId; }
    public String getCrossDockTargetType() { return crossDockTargetType; }
    public void setCrossDockTargetType(String crossDockTargetType) { this.crossDockTargetType = crossDockTargetType; }
    public Long getCrossDockTargetId() { return crossDockTargetId; }
    public void setCrossDockTargetId(Long crossDockTargetId) { this.crossDockTargetId = crossDockTargetId; }
    public Long getWaveId() { return waveId; }
    public void setWaveId(Long waveId) { this.waveId = waveId; }
    public WmsEnums.TransferOrderStatus getStatus() { return status; }
    public void setStatus(WmsEnums.TransferOrderStatus status) { this.status = status; }
    public Long getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Long assignedTo) { this.assignedTo = assignedTo; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
