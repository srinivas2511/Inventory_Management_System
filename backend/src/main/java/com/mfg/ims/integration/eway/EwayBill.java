package com.mfg.ims.integration.eway;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "eway_bills", schema = "ims")
public class EwayBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "dispatch_id")
    private Long dispatchId;

    @Column(name = "eway_bill_number", length = 50)
    private String ewayBillNumber;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "transporter_id")
    private Long transporterId;

    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;

    @Column(name = "distance_km")
    private Integer distanceKm;

    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long v) { this.invoiceId = v; }
    public Long getDispatchId() { return dispatchId; }
    public void setDispatchId(Long v) { this.dispatchId = v; }
    public String getEwayBillNumber() { return ewayBillNumber; }
    public void setEwayBillNumber(String v) { this.ewayBillNumber = v; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant v) { this.generatedAt = v; }
    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant v) { this.validUntil = v; }
    public Long getTransporterId() { return transporterId; }
    public void setTransporterId(Long v) { this.transporterId = v; }
    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String v) { this.vehicleNumber = v; }
    public Integer getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Integer v) { this.distanceKm = v; }
    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String v) { this.requestPayload = v; }
    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String v) { this.responsePayload = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long v) { this.createdBy = v; }
}
