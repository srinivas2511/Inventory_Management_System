package com.mfg.ims.production.domain;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "production_order_templates", schema = "ims")
public class ProductionOrderTemplate extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "bom_id")
    private Long bomId;

    @Column(name = "planned_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal plannedQty = BigDecimal.ONE;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays = 1;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }
    public Long getBomId() { return bomId; }
    public void setBomId(Long v) { this.bomId = v; }
    public BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(BigDecimal v) { this.plannedQty = v; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(int v) { this.leadTimeDays = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
