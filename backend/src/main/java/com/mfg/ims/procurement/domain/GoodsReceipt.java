package com.mfg.ims.procurement.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "goods_receipts", schema = "ims")
public class GoodsReceipt extends BaseEntity {

    @Column(name = "gr_number", nullable = false, unique = true, length = 20)
    private String grNumber;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "supplier_challan_no", length = 40)
    private String supplierChallanNo;

    @Column(name = "vehicle_no", length = 20)
    private String vehicleNo;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "POSTED";

    @Column(name = "received_by", nullable = false)
    private Long receivedBy;

    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoodsReceiptItem> items = new ArrayList<>();

    @jakarta.persistence.Column(name = "plant_id")
    private Long plantId;

    public String getGrNumber() { return grNumber; }
    public void setGrNumber(String v) { this.grNumber = v; }

    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long v) { this.purchaseOrderId = v; }

    public LocalDate getReceiptDate() { return receiptDate; }
    public void setReceiptDate(LocalDate v) { this.receiptDate = v; }

    public String getSupplierChallanNo() { return supplierChallanNo; }
    public void setSupplierChallanNo(String v) { this.supplierChallanNo = v; }

    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String v) { this.vehicleNo = v; }

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long v) { this.warehouseId = v; }

        public Long getPlantId() { return plantId; }
    public void setPlantId(Long plantId) { this.plantId = plantId; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public Long getReceivedBy() { return receivedBy; }
    public void setReceivedBy(Long v) { this.receivedBy = v; }

    public List<GoodsReceiptItem> getItems() { return items; }
}
