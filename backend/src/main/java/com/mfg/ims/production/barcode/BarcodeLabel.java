package com.mfg.ims.production.barcode;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "barcode_labels", schema = "ims")
public class BarcodeLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    @Column(name = "label_type", nullable = false, length = 20)
    private String labelType;

    @Column(name = "ref_type", nullable = false, length = 20)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(name = "barcode_value", nullable = false, length = 100)
    private String barcodeValue;

    @Column(name = "qr_data", columnDefinition = "TEXT")
    private String qrData;

    @Column(name = "printed_at")
    private Instant printedAt;

    @Column(name = "printed_by")
    private Long printedBy;

    @Column(name = "print_count", nullable = false)
    private int printCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ DEFAULT now()")
    private Instant createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getLabelType() { return labelType; }
    public void setLabelType(String labelType) { this.labelType = labelType; }
    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public String getBarcodeValue() { return barcodeValue; }
    public void setBarcodeValue(String barcodeValue) { this.barcodeValue = barcodeValue; }
    public String getQrData() { return qrData; }
    public void setQrData(String qrData) { this.qrData = qrData; }
    public Instant getPrintedAt() { return printedAt; }
    public void setPrintedAt(Instant printedAt) { this.printedAt = printedAt; }
    public Long getPrintedBy() { return printedBy; }
    public void setPrintedBy(Long printedBy) { this.printedBy = printedBy; }
    public int getPrintCount() { return printCount; }
    public void setPrintCount(int printCount) { this.printCount = printCount; }
    public Instant getCreatedAt() { return createdAt; }
}
