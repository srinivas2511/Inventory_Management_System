package com.mfg.ims.sales.challan;

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
@Table(name = "delivery_challans", schema = "ims")
public class DeliveryChallan extends BaseEntity {

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(name = "doc_date", nullable = false)
    private LocalDate docDate;

    @Column(name = "status", nullable = false, length = 25)
    private String status = "DRAFT";

    @Column(name = "challan_kind", nullable = false, length = 20)
    private String challanKind = "DELIVERY";

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "oc_id")
    private Long ocId;

    @Column(name = "sales_order_id")
    private Long salesOrderId;

    @Column(name = "dispatch_id")
    private Long dispatchId;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    @Column(name = "vehicle_no", length = 20)
    private String vehicleNo;

    @Column(name = "driver_name", length = 100)
    private String driverName;

    @Column(name = "lr_no", length = 60)
    private String lrNo;

    @Column(name = "courier", length = 100)
    private String courier;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    @Column(name = "grand_total", precision = 18, scale = 4)
    private BigDecimal grandTotal;

    @OneToMany(mappedBy = "challan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<DeliveryChallanItem> items = new ArrayList<>();

    public String getDocNo() { return docNo; }
    public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; }
    public void setDocDate(LocalDate v) { this.docDate = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getChallanKind() { return challanKind; }
    public void setChallanKind(String v) { this.challanKind = v; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long v) { this.customerId = v; }
    public Long getOcId() { return ocId; }
    public void setOcId(Long v) { this.ocId = v; }
    public Long getSalesOrderId() { return salesOrderId; }
    public void setSalesOrderId(Long v) { this.salesOrderId = v; }
    public Long getDispatchId() { return dispatchId; }
    public void setDispatchId(Long v) { this.dispatchId = v; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String v) { this.deliveryAddress = v; }
    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String v) { this.vehicleNo = v; }
    public String getDriverName() { return driverName; }
    public void setDriverName(String v) { this.driverName = v; }
    public String getLrNo() { return lrNo; }
    public void setLrNo(String v) { this.lrNo = v; }
    public String getCourier() { return courier; }
    public void setCourier(String v) { this.courier = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal v) { this.grandTotal = v; }
    public List<DeliveryChallanItem> getItems() { return items; }
}
