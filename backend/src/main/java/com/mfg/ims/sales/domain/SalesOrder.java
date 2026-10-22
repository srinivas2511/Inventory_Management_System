package com.mfg.ims.sales.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.mfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "sales_orders", schema = "ims")
public class SalesOrder extends BaseEntity {

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "required_date")
    private LocalDate requiredDate;

    @Column(name = "delivery_address", length = 400)
    private String deliveryAddress;

    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    @jakarta.persistence.Column(name = "plant_id")
    private Long plantId;

    public SalesOrder() {}

    public String getOrderNumber() { return orderNumber; }
    public Long getCustomerId() { return customerId; }
    public LocalDate getOrderDate() { return orderDate; }
    public LocalDate getRequiredDate() { return requiredDate; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getPaymentTerms() { return paymentTerms; }
    public String getNotes() { return notes; }
        public Long getPlantId() { return plantId; }
    public void setPlantId(Long plantId) { this.plantId = plantId; }
    public String getStatus() { return status; }
    public Long getSubmittedBy() { return submittedBy; }
    public Long getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public Long getCancelledBy() { return cancelledBy; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancelReason() { return cancelReason; }

    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
    public void setRequiredDate(LocalDate requiredDate) { this.requiredDate = requiredDate; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setStatus(String status) { this.status = status; }
    public void setSubmittedBy(Long submittedBy) { this.submittedBy = submittedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public void setCancelledBy(Long cancelledBy) { this.cancelledBy = cancelledBy; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
}
