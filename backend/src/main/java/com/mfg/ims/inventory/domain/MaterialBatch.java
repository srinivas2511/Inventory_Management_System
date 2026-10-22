package com.mfg.ims.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.mfg.ims.common.domain.BaseEntity;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.partner.domain.Supplier;

@Entity
@Table(name = "material_batches", schema = "ims")
public class MaterialBatch extends BaseEntity {

    @Column(name = "batch_number", nullable = false, unique = true, length = 30)
    private String batchNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "purchase_order_id")
    private Long purchaseOrderId;

    @Column(name = "goods_receipt_item_id")
    private Long goodsReceiptItemId;

    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "received_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal receivedQty;

    @Column(name = "remaining_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal remainingQty;

    @Column(name = "heat_number", length = 40)
    private String heatNumber;

    @Column(name = "certificate_number", length = 40)
    private String certificateNumber;

    @Column(name = "unit_cost", precision = 18, scale = 4)
    private BigDecimal unitCost;

    @Column(nullable = false, length = 15)
    private String status = "QUARANTINE";

    public MaterialBatch() {}

    public String getBatchNumber() { return batchNumber; }
    public Material getMaterial() { return material; }
    public Supplier getSupplier() { return supplier; }
    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public Long getGoodsReceiptItemId() { return goodsReceiptItemId; }
    public LocalDate getManufacturingDate() { return manufacturingDate; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public BigDecimal getReceivedQty() { return receivedQty; }
    public BigDecimal getRemainingQty() { return remainingQty; }
    public String getHeatNumber() { return heatNumber; }
    public String getCertificateNumber() { return certificateNumber; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getStatus() { return status; }

    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    public void setMaterial(Material material) { this.material = material; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    public void setGoodsReceiptItemId(Long goodsReceiptItemId) { this.goodsReceiptItemId = goodsReceiptItemId; }
    public void setManufacturingDate(LocalDate manufacturingDate) { this.manufacturingDate = manufacturingDate; }
    public void setReceivedDate(LocalDate receivedDate) { this.receivedDate = receivedDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public void setReceivedQty(BigDecimal receivedQty) { this.receivedQty = receivedQty; }
    public void setRemainingQty(BigDecimal remainingQty) { this.remainingQty = remainingQty; }
    public void setHeatNumber(String heatNumber) { this.heatNumber = heatNumber; }
    public void setCertificateNumber(String certificateNumber) { this.certificateNumber = certificateNumber; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public void setStatus(String status) { this.status = status; }

    public boolean isAvailableForIssue() {
        return "AVAILABLE".equals(status);
    }
}
