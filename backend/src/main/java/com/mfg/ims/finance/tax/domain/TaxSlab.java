package com.mfg.ims.finance.tax.domain;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One component of a GST slab for a supply type: slab GST18 on an intra-state supply is two rows, CGST9 and SGST9. */
@Entity
@Table(name = "tax_slabs", schema = "ims")
public class TaxSlab extends BaseEntity {

    @Column(name = "slab_code", nullable = false, length = 15)
    private String slabCode;

    @Column(nullable = false, length = 100)
    private String description;

    @Column(name = "supply_type", nullable = false, length = 5)
    private String supplyType;

    @Column(name = "tax_code_id", nullable = false)
    private Long taxCodeId;

    private boolean active = true;

    public String getSlabCode() { return slabCode; }
    public String getDescription() { return description; }
    public String getSupplyType() { return supplyType; }
    public Long getTaxCodeId() { return taxCodeId; }
    public boolean isActive() { return active; }
}
