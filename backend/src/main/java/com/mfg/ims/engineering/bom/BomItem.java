package com.mfg.ims.engineering.bom;

import java.math.BigDecimal;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "bom_items", schema = "ims")
public class BomItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bom_id", nullable = false)
    private Bom bom;

    // For JPQL join convenience — not mapped as FK, referenced via parent bom
    @Column(name = "bom_id", insertable = false, updatable = false)
    private Long bomId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_kind", nullable = false, length = 12)
    private String itemKind;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "operation_id")
    private Long operationId;

    @Column(name = "required_qty", precision = 18, scale = 6)
    private BigDecimal requiredQty;

    @Column(name = "uom", length = 10)
    private String uom;

    @Column(name = "scrap_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal scrapPct = BigDecimal.ZERO;

    @Column(name = "instruction", length = 300)
    private String instruction;

    public BomItem() {}

    public Bom getBom() { return bom; }
    public void setBom(Bom v) { this.bom = v; }

    public Long getBomId() { return bomId; }

    public int getLineNo() { return lineNo; }
    public void setLineNo(int v) { this.lineNo = v; }

    public String getItemKind() { return itemKind; }
    public void setItemKind(String v) { this.itemKind = v; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }

    public Long getOperationId() { return operationId; }
    public void setOperationId(Long v) { this.operationId = v; }

    public BigDecimal getRequiredQty() { return requiredQty; }
    public void setRequiredQty(BigDecimal v) { this.requiredQty = v; }

    public String getUom() { return uom; }
    public void setUom(String v) { this.uom = v; }

    public BigDecimal getScrapPct() { return scrapPct; }
    public void setScrapPct(BigDecimal v) { this.scrapPct = v; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String v) { this.instruction = v; }
}
