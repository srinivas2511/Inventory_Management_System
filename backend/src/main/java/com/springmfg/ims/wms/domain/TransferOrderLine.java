package com.springmfg.ims.wms.domain;

import java.time.Instant;
import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "transfer_order_lines", schema = "ims")
public class TransferOrderLine extends BaseEntity {

    @Column(name = "transfer_order_id", nullable = false)
    private Long transferOrderId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_type", nullable = false, length = 10)
    private String itemType;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "stock_status", length = 20)
    private String stockStatus;

    @Column(name = "from_bin_id")
    private Long fromBinId;

    @Column(name = "to_bin_id")
    private Long toBinId;

    @Column(name = "from_location_id", nullable = false)
    private Long fromLocationId;

    @Column(name = "to_location_id")
    private Long toLocationId;

    @Column(name = "planned_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal plannedQty;

    @Column(name = "confirmed_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal confirmedQty = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private WmsEnums.TransferLineStatus status = WmsEnums.TransferLineStatus.OPEN;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "warning", length = 300)
    private String warning;

    public TransferOrderLine() {
    }

    public Long getTransferOrderId() { return transferOrderId; }
    public void setTransferOrderId(Long transferOrderId) { this.transferOrderId = transferOrderId; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int lineNo) { this.lineNo = lineNo; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getStockStatus() { return stockStatus; }
    public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
    public Long getFromBinId() { return fromBinId; }
    public void setFromBinId(Long fromBinId) { this.fromBinId = fromBinId; }
    public Long getToBinId() { return toBinId; }
    public void setToBinId(Long toBinId) { this.toBinId = toBinId; }
    public Long getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(Long fromLocationId) { this.fromLocationId = fromLocationId; }
    public Long getToLocationId() { return toLocationId; }
    public void setToLocationId(Long toLocationId) { this.toLocationId = toLocationId; }
    public BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(BigDecimal plannedQty) { this.plannedQty = plannedQty; }
    public BigDecimal getConfirmedQty() { return confirmedQty; }
    public void setConfirmedQty(BigDecimal confirmedQty) { this.confirmedQty = confirmedQty; }
    public WmsEnums.TransferLineStatus getStatus() { return status; }
    public void setStatus(WmsEnums.TransferLineStatus status) { this.status = status; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }
    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
}
