package com.mfg.ims.sales.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.mfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "dispatches", schema = "ims")
public class Dispatch extends BaseEntity {

    @Column(name = "dispatch_number", nullable = false, unique = true, length = 20)
    private String dispatchNumber;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "dispatch_date", nullable = false)
    private LocalDate dispatchDate;

    @Column(length = 100)
    private String transporter;

    @Column(name = "vehicle_no", length = 20)
    private String vehicleNo;

    @Column(name = "lr_number", length = 30)
    private String lrNumber;

    @Column(name = "delivery_address", nullable = false, length = 400)
    private String deliveryAddress;

    @Column(name = "invoice_reference", length = 40)
    private String invoiceReference;

    @Column(nullable = false, length = 12)
    private String status = "DISPATCHED";

    @Column(name = "dispatched_by", nullable = false)
    private Long dispatchedBy;

    @Column(length = 300)
    private String notes;

    @jakarta.persistence.Column(name = "plant_id")
    private Long plantId;

    public Dispatch() {}

    public String getDispatchNumber() { return dispatchNumber; }
    public Long getSalesOrderId() { return salesOrderId; }
    public Long getCustomerId() { return customerId; }
    public LocalDate getDispatchDate() { return dispatchDate; }
    public String getTransporter() { return transporter; }
    public String getVehicleNo() { return vehicleNo; }
    public String getLrNumber() { return lrNumber; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getInvoiceReference() { return invoiceReference; }
        public Long getPlantId() { return plantId; }
    public void setPlantId(Long plantId) { this.plantId = plantId; }
    public String getStatus() { return status; }
    public Long getDispatchedBy() { return dispatchedBy; }
    public String getNotes() { return notes; }

    public void setDispatchNumber(String dispatchNumber) { this.dispatchNumber = dispatchNumber; }
    public void setSalesOrderId(Long salesOrderId) { this.salesOrderId = salesOrderId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public void setDispatchDate(LocalDate dispatchDate) { this.dispatchDate = dispatchDate; }
    public void setTransporter(String transporter) { this.transporter = transporter; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }
    public void setLrNumber(String lrNumber) { this.lrNumber = lrNumber; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public void setInvoiceReference(String invoiceReference) { this.invoiceReference = invoiceReference; }
    public void setStatus(String status) { this.status = status; }
    public void setDispatchedBy(Long dispatchedBy) { this.dispatchedBy = dispatchedBy; }
    public void setNotes(String notes) { this.notes = notes; }
}
