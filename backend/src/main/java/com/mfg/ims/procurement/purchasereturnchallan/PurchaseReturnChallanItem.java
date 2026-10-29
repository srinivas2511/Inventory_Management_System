package com.mfg.ims.procurement.purchasereturnchallan;

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

@Entity
@Table(name = "purchase_return_challan_items", schema = "ims")
public class PurchaseReturnChallanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challan_id", nullable = false)
    private PurchaseReturnChallan challan;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal qty;

    @Column(name = "uom", nullable = false, length = 20)
    private String uom = "PCS";

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    public Long getId() { return id; }
    public PurchaseReturnChallan getChallan() { return challan; }
    public void setChallan(PurchaseReturnChallan v) { this.challan = v; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int v) { this.lineNo = v; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal v) { this.qty = v; }
    public String getUom() { return uom; }
    public void setUom(String v) { this.uom = v; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal v) { this.unitPrice = v; }
}
