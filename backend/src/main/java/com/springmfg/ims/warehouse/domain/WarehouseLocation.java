package com.springmfg.ims.warehouse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "warehouse_locations", schema = "ims")
public class WarehouseLocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "location_code", nullable = false, length = 20)
    private String locationCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "location_type", nullable = false, length = 15)
    private String locationType;

    @Column(nullable = false)
    private boolean active = true;

    public WarehouseLocation() {}

    public Warehouse getWarehouse() { return warehouse; }
    public String getLocationCode() { return locationCode; }
    public String getName() { return name; }
    public String getLocationType() { return locationType; }
    public boolean isActive() { return active; }

    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public void setName(String name) { this.name = name; }
    public void setLocationType(String locationType) { this.locationType = locationType; }
    public void setActive(boolean active) { this.active = active; }
}
