package com.mfg.ims.procurement.purchasereturnchallan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_return_challans", schema = "ims")
public class PurchaseReturnChallan extends BaseEntity {

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(name = "doc_date", nullable = false)
    private LocalDate docDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "purchase_return_id")
    private Long purchaseReturnId;

    @Column(name = "vehicle_no", length = 20)
    private String vehicleNo;

    @Column(name = "lr_no", length = 60)
    private String lrNo;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    @Column(name = "grand_total", precision = 18, scale = 4)
    private BigDecimal grandTotal;

    @OneToMany(mappedBy = "challan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<PurchaseReturnChallanItem> items = new ArrayList<>();

    public String getDocNo() { return docNo; }
    public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; }
    public void setDocDate(LocalDate v) { this.docDate = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long v) { this.supplierId = v; }
    public Long getPurchaseReturnId() { return purchaseReturnId; }
    public void setPurchaseReturnId(Long v) { this.purchaseReturnId = v; }
    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String v) { this.vehicleNo = v; }
    public String getLrNo() { return lrNo; }
    public void setLrNo(String v) { this.lrNo = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal v) { this.grandTotal = v; }
    public List<PurchaseReturnChallanItem> getItems() { return items; }
}
