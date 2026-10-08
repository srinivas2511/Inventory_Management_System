package com.springmfg.ims.warehouse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "warehouses", schema = "ims")
public class Warehouse extends BaseEntity {

    @Column(name = "warehouse_code", nullable = false, unique = true, length = 20)
    private String warehouseCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "plant_id")
    private Long plantId;

    public Warehouse() {}

    public String getWarehouseCode() { return warehouseCode; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public boolean isActive() { return active; }
    public Long getPlantId() { return plantId; }
    public void setPlantId(Long plantId) { this.plantId = plantId; }

    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }
    public void setName(String name) { this.name = name; }
    public void setAddress(String address) { this.address = address; }
    public void setActive(boolean active) { this.active = active; }
}
