package com.mfg.ims.integration.gst;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "gst_einvoices", schema = "ims")
public class GstEInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispatch_id", nullable = false, unique = true)
    private Long dispatchId;

    @Column(name = "irn", length = 64)
    private String irn;

    @Column(name = "ack_no", length = 30)
    private String ackNo;

    @Column(name = "ack_date")
    private Instant ackDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "GENERATED";

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "gstn_response", columnDefinition = "TEXT")
    private String gstnResponse;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    @Column(name = "generated_by")
    private Long generatedBy;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    public Long getId() { return id; }
    public Long getDispatchId() { return dispatchId; }
    public void setDispatchId(Long dispatchId) { this.dispatchId = dispatchId; }
    public String getIrn() { return irn; }
    public void setIrn(String irn) { this.irn = irn; }
    public String getAckNo() { return ackNo; }
    public void setAckNo(String ackNo) { this.ackNo = ackNo; }
    public Instant getAckDate() { return ackDate; }
    public void setAckDate(Instant ackDate) { this.ackDate = ackDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getGstnResponse() { return gstnResponse; }
    public void setGstnResponse(String gstnResponse) { this.gstnResponse = gstnResponse; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public Long getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(Long generatedBy) { this.generatedBy = generatedBy; }
    public Long getCompanyId() { return companyId; }
}
