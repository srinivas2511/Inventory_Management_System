package com.springmfg.ims.production.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "production_orders", schema = "ims")
public class ProductionOrder extends BaseEntity {

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "bom_id", nullable = false)
    private Long bomId;

    @Column(name = "planned_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal plannedQty;

    @Column(name = "good_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal goodQty = BigDecimal.ZERO;

    @Column(name = "rejected_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal rejectedQty = BigDecimal.ZERO;

    @Column(name = "scrap_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal scrapQty = BigDecimal.ZERO;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "expected_completion_date")
    private LocalDate expectedCompletionDate;

    @Column(name = "actual_start_at")
    private Instant actualStartAt;

    @Column(name = "actual_completion_at")
    private Instant actualCompletionAt;

    @Column(name = "production_line", length = 40)
    private String productionLine;

    @Column(name = "primary_machine_id")
    private Long primaryMachineId;

    @Column(name = "supervisor_id")
    private Long supervisorId;

    @Column(name = "sales_order_id")
    private Long salesOrderId;

    @Column(name = "priority", nullable = false)
    private short priority = 3;

    @Column(name = "status", nullable = false, length = 15)
    private String status = "DRAFT";

    @Column(name = "released_by")
    private Long releasedBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "remarks", length = 500)
    private String remarks;

    public ProductionOrder() {}

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String v) { this.orderNumber = v; }

    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }

    public Long getBomId() { return bomId; }
    public void setBomId(Long v) { this.bomId = v; }

    public BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(BigDecimal v) { this.plannedQty = v; }

    public BigDecimal getGoodQty() { return goodQty; }
    public void setGoodQty(BigDecimal v) { this.goodQty = v; }

    public BigDecimal getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(BigDecimal v) { this.rejectedQty = v; }

    public BigDecimal getScrapQty() { return scrapQty; }
    public void setScrapQty(BigDecimal v) { this.scrapQty = v; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }

    public LocalDate getExpectedCompletionDate() { return expectedCompletionDate; }
    public void setExpectedCompletionDate(LocalDate v) { this.expectedCompletionDate = v; }

    public Instant getActualStartAt() { return actualStartAt; }
    public void setActualStartAt(Instant v) { this.actualStartAt = v; }

    public Instant getActualCompletionAt() { return actualCompletionAt; }
    public void setActualCompletionAt(Instant v) { this.actualCompletionAt = v; }

    public String getProductionLine() { return productionLine; }
    public void setProductionLine(String v) { this.productionLine = v; }

    public Long getPrimaryMachineId() { return primaryMachineId; }
    public void setPrimaryMachineId(Long v) { this.primaryMachineId = v; }

    public Long getSupervisorId() { return supervisorId; }
    public void setSupervisorId(Long v) { this.supervisorId = v; }

    public Long getSalesOrderId() { return salesOrderId; }
    public void setSalesOrderId(Long v) { this.salesOrderId = v; }

    public short getPriority() { return priority; }
    public void setPriority(short v) { this.priority = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public Long getReleasedBy() { return releasedBy; }
    public void setReleasedBy(Long v) { this.releasedBy = v; }

    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant v) { this.releasedAt = v; }

    public Long getClosedBy() { return closedBy; }
    public void setClosedBy(Long v) { this.closedBy = v; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant v) { this.closedAt = v; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
}
