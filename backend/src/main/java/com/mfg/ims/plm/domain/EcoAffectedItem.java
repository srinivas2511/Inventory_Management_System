package com.mfg.ims.plm.domain;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "eco_affected_items", schema = "ims")
public class EcoAffectedItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eco_id", nullable = false)
    private Eco eco;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 10)
    private EcoItemType itemType;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "old_bom_id")
    private Long oldBomId;

    @Column(name = "new_bom_id")
    private Long newBomId;

    @Column(name = "change_description", nullable = false, length = 300)
    private String changeDescription;

    public Eco getEco() { return eco; }
    public void setEco(Eco v) { this.eco = v; }
    public EcoItemType getItemType() { return itemType; }
    public void setItemType(EcoItemType v) { this.itemType = v; }
    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }
    public Long getOldBomId() { return oldBomId; }
    public void setOldBomId(Long v) { this.oldBomId = v; }
    public Long getNewBomId() { return newBomId; }
    public void setNewBomId(Long v) { this.newBomId = v; }
    public String getChangeDescription() { return changeDescription; }
    public void setChangeDescription(String v) { this.changeDescription = v; }
}
