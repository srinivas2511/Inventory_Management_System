package com.springmfg.ims.production.domain;

import java.math.BigDecimal;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "material_requests", schema = "ims")
public class MaterialRequest extends BaseEntity {

    @Column(name = "production_order_id")
    private Long productionOrderId;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "requested_qty", precision = 18, scale = 3)
    private BigDecimal requestedQty;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "status", nullable = false, length = 15)
    private String status = "OPEN";

    @Column(name = "fulfilled_issue_id")
    private Long fulfilledIssueId;

    public MaterialRequest() {}

    public Long getProductionOrderId() { return productionOrderId; }
    public void setProductionOrderId(Long v) { this.productionOrderId = v; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }

    public BigDecimal getRequestedQty() { return requestedQty; }
    public void setRequestedQty(BigDecimal v) { this.requestedQty = v; }

    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long v) { this.requestedBy = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public Long getFulfilledIssueId() { return fulfilledIssueId; }
    public void setFulfilledIssueId(Long v) { this.fulfilledIssueId = v; }
}
