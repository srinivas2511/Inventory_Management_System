package com.springmfg.ims.engineering.routing;

import java.math.BigDecimal;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_routing", schema = "ims")
public class ProductRouting extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "operation_id", nullable = false)
    private Long operationId;

    @Column(name = "machine_id")
    private Long machineId;

    @Column(name = "standard_time_min", precision = 10, scale = 2)
    private BigDecimal standardTimeMin;

    @Column(name = "setup_time_min", precision = 10, scale = 2)
    private BigDecimal setupTimeMin;

    @Column(name = "inspection_point", nullable = false)
    private boolean inspectionPoint = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public ProductRouting() {}

    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }

    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int v) { this.sequenceNo = v; }

    public Long getOperationId() { return operationId; }
    public void setOperationId(Long v) { this.operationId = v; }

    public Long getMachineId() { return machineId; }
    public void setMachineId(Long v) { this.machineId = v; }

    public BigDecimal getStandardTimeMin() { return standardTimeMin; }
    public void setStandardTimeMin(BigDecimal v) { this.standardTimeMin = v; }

    public BigDecimal getSetupTimeMin() { return setupTimeMin; }
    public void setSetupTimeMin(BigDecimal v) { this.setupTimeMin = v; }

    public boolean isInspectionPoint() { return inspectionPoint; }
    public void setInspectionPoint(boolean v) { this.inspectionPoint = v; }

    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
