package com.mfg.ims.production.costrollup;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cost_rollup_logs", schema = "ims")
public class CostRollupLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "rolled_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal rolledCost;

    @Column(name = "bom_id")
    private Long bomId;

    @Column(name = "depth", nullable = false)
    private int depth = 0;

    @Column(name = "rolled_at", nullable = false)
    private Instant rolledAt;

    @Column(name = "rolled_by")
    private Long rolledBy;

    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public BigDecimal getRolledCost() { return rolledCost; }
    public void setRolledCost(BigDecimal rolledCost) { this.rolledCost = rolledCost; }
    public Long getBomId() { return bomId; }
    public void setBomId(Long bomId) { this.bomId = bomId; }
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
    public Instant getRolledAt() { return rolledAt; }
    public void setRolledAt(Instant rolledAt) { this.rolledAt = rolledAt; }
    public Long getRolledBy() { return rolledBy; }
    public void setRolledBy(Long rolledBy) { this.rolledBy = rolledBy; }
}
