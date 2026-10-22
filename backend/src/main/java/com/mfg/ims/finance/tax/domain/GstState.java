package com.mfg.ims.finance.tax.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Reference data: a GST state code and whether it is a union territory without a legislature (CGST + UTGST). */
@Entity
@Table(name = "gst_states", schema = "ims")
public class GstState {

    @Id
    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "union_territory", nullable = false)
    private boolean unionTerritory;

    public String getStateCode() { return stateCode; }
    public String getName() { return name; }
    public boolean isUnionTerritory() { return unionTerritory; }
}
