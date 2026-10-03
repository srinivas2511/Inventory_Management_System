package com.springmfg.ims.wms.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "warehouse_zones", schema = "ims")
public class WarehouseZone extends BaseEntity {

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "zone_code", nullable = false, length = 10)
    private String zoneCode;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 15)
    private WmsEnums.ZoneType zoneType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public WarehouseZone() {
    }

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getZoneCode() { return zoneCode; }
    public void setZoneCode(String zoneCode) { this.zoneCode = zoneCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public WmsEnums.ZoneType getZoneType() { return zoneType; }
    public void setZoneType(WmsEnums.ZoneType zoneType) { this.zoneType = zoneType; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
