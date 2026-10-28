package com.mfg.ims.sales.challan;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "delivery_challan_items", schema = "ims")
public class DeliveryChallanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challan_id", nullable = false)
    private DeliveryChallan challan;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_kind", nullable = false, length = 10)
    private String itemKind = "PRODUCT";

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal qty;

    @Column(name = "uom", length = 10)
    private String uom;

    @Column(name = "rate", precision = 18, scale = 4)
    private BigDecimal rate;

    @Column(name = "line_total", precision = 18, scale = 4)
    private BigDecimal lineTotal;

    @Column(name = "batch_no", length = 50)
    private String batchNo;

    @Column(name = "oc_line_id")
    private Long ocLineId;

    public Long getId() { return id; }
    public DeliveryChallan getChallan() { return challan; }
    public void setChallan(DeliveryChallan v) { this.challan = v; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int v) { this.lineNo = v; }
    public String getItemKind() { return itemKind; }
    public void setItemKind(String v) { this.itemKind = v; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }
    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal v) { this.qty = v; }
    public String getUom() { return uom; }
    public void setUom(String v) { this.uom = v; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal v) { this.rate = v; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal v) { this.lineTotal = v; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String v) { this.batchNo = v; }
    public Long getOcLineId() { return ocLineId; }
    public void setOcLineId(Long v) { this.ocLineId = v; }
}
