package com.mfg.ims.quality.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rejections", schema = "ims")
public class Rejection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inspection_id")
    private Long inspectionId;

    @Column(name = "production_output_id")
    private Long productionOutputId;

    @Column(name = "rejection_reason_id", nullable = false)
    private Long rejectionReasonId;

    @Column(name = "item_type", length = 10)
    private String itemType;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "disposition", nullable = false, length = 15)
    private String disposition = "SCRAP";

    @Column(name = "inventory_transaction_id")
    private Long inventoryTransactionId;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    public Long getId() { return id; }

    public Long getInspectionId() { return inspectionId; }
    public void setInspectionId(Long v) { this.inspectionId = v; }

    public Long getProductionOutputId() { return productionOutputId; }
    public void setProductionOutputId(Long v) { this.productionOutputId = v; }

    public Long getRejectionReasonId() { return rejectionReasonId; }
    public void setRejectionReasonId(Long v) { this.rejectionReasonId = v; }

    public String getItemType() { return itemType; }
    public void setItemType(String v) { this.itemType = v; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }

    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long v) { this.batchId = v; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal v) { this.quantity = v; }

    public String getDisposition() { return disposition; }
    public void setDisposition(String v) { this.disposition = v; }

    public Long getInventoryTransactionId() { return inventoryTransactionId; }
    public void setInventoryTransactionId(Long v) { this.inventoryTransactionId = v; }

    public Long getRecordedBy() { return recordedBy; }
    public void setRecordedBy(Long v) { this.recordedBy = v; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant v) { this.recordedAt = v; }
}
