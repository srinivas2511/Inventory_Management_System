package com.mfg.ims.production.jobwork;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "job_work_orders", schema = "ims")
public class JobWorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(name = "doc_date", nullable = false)
    private LocalDate docDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "challan_no", length = 40)
    private String challanNo;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expected_return")
    private LocalDate expectedReturn;

    @Column(name = "actual_return")
    private LocalDate actualReturn;

    @Column(name = "job_charges", nullable = false, precision = 18, scale = 4)
    private BigDecimal jobCharges = BigDecimal.ZERO;

    @Column(name = "scrap_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal scrapQty = BigDecimal.ZERO;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "jwo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<JobWorkOrderItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getDocNo() { return docNo; }
    public void setDocNo(String docNo) { this.docNo = docNo; }
    public LocalDate getDocDate() { return docDate; }
    public void setDocDate(LocalDate docDate) { this.docDate = docDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getChallanNo() { return challanNo; }
    public void setChallanNo(String challanNo) { this.challanNo = challanNo; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getExpectedReturn() { return expectedReturn; }
    public void setExpectedReturn(LocalDate expectedReturn) { this.expectedReturn = expectedReturn; }
    public LocalDate getActualReturn() { return actualReturn; }
    public void setActualReturn(LocalDate actualReturn) { this.actualReturn = actualReturn; }
    public BigDecimal getJobCharges() { return jobCharges; }
    public void setJobCharges(BigDecimal jobCharges) { this.jobCharges = jobCharges; }
    public BigDecimal getScrapQty() { return scrapQty; }
    public void setScrapQty(BigDecimal scrapQty) { this.scrapQty = scrapQty; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Long getVersion() { return version; }
    public List<JobWorkOrderItem> getItems() { return items; }
}
