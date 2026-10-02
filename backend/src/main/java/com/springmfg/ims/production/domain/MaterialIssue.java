package com.springmfg.ims.production.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "material_issues", schema = "ims")
public class MaterialIssue extends BaseEntity {

    @Column(name = "issue_number", nullable = false, unique = true, length = 20)
    private String issueNumber;

    @Column(name = "production_order_id", nullable = false)
    private Long productionOrderId;

    @Column(name = "issue_date", nullable = false)
    private Instant issueDate = Instant.now();

    @Column(name = "from_warehouse_id")
    private Long fromWarehouseId;

    @Column(name = "issued_by", nullable = false)
    private Long issuedBy;

    @Column(name = "received_by")
    private Long receivedBy;

    @Column(name = "remarks", length = 300)
    private String remarks;

    @OneToMany(mappedBy = "materialIssue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<MaterialIssueLine> lines = new ArrayList<>();

    public MaterialIssue() {}

    public String getIssueNumber() { return issueNumber; }
    public void setIssueNumber(String v) { this.issueNumber = v; }

    public Long getProductionOrderId() { return productionOrderId; }
    public void setProductionOrderId(Long v) { this.productionOrderId = v; }

    public Instant getIssueDate() { return issueDate; }
    public void setIssueDate(Instant v) { this.issueDate = v; }

    public Long getFromWarehouseId() { return fromWarehouseId; }
    public void setFromWarehouseId(Long v) { this.fromWarehouseId = v; }

    public Long getIssuedBy() { return issuedBy; }
    public void setIssuedBy(Long v) { this.issuedBy = v; }

    public Long getReceivedBy() { return receivedBy; }
    public void setReceivedBy(Long v) { this.receivedBy = v; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }

    public List<MaterialIssueLine> getLines() { return lines; }
}
