package com.mfg.ims.procurement.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "goods_receipt_items", schema = "ims")
public class GoodsReceiptItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt goodsReceipt;

    @Column(name = "po_item_id")
    private Long poItemId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "received_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal receivedQty;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "heat_number", length = 40)
    private String heatNumber;

    @Column(name = "certificate_number", length = 40)
    private String certificateNumber;

    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    @Column(name = "material_batch_id")
    private Long materialBatchId;

    @Column(name = "inspection_required", nullable = false)
    private boolean inspectionRequired = true;

    public GoodsReceipt getGoodsReceipt() { return goodsReceipt; }
    public void setGoodsReceipt(GoodsReceipt v) { this.goodsReceipt = v; }

    public Long getPoItemId() { return poItemId; }
    public void setPoItemId(Long v) { this.poItemId = v; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }

    public BigDecimal getReceivedQty() { return receivedQty; }
    public void setReceivedQty(BigDecimal v) { this.receivedQty = v; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long v) { this.locationId = v; }

    public String getHeatNumber() { return heatNumber; }
    public void setHeatNumber(String v) { this.heatNumber = v; }

    public String getCertificateNumber() { return certificateNumber; }
    public void setCertificateNumber(String v) { this.certificateNumber = v; }

    public LocalDate getManufacturingDate() { return manufacturingDate; }
    public void setManufacturingDate(LocalDate v) { this.manufacturingDate = v; }

    public Long getMaterialBatchId() { return materialBatchId; }
    public void setMaterialBatchId(Long v) { this.materialBatchId = v; }

    public boolean isInspectionRequired() { return inspectionRequired; }
    public void setInspectionRequired(boolean v) { this.inspectionRequired = v; }
}
