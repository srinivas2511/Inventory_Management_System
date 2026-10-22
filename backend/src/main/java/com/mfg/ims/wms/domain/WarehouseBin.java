package com.mfg.ims.wms.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "warehouse_bins", schema = "ims")
public class WarehouseBin extends BaseEntity {

    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "bin_code", nullable = false, length = 20)
    private String binCode;

    @Column(name = "aisle", length = 10)
    private String aisle;

    @Column(name = "rack", length = 10)
    private String rack;

    @Column(name = "level", length = 10)
    private String level;

    @Column(name = "bin_position", length = 10)
    private String binPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "bin_type", nullable = false, length = 15)
    private WmsEnums.BinType binType;

    @Column(name = "max_weight_kg", precision = 10, scale = 3)
    private BigDecimal maxWeightKg;

    @Column(name = "max_volume_m3", precision = 10, scale = 4)
    private BigDecimal maxVolumeM3;

    @Column(name = "current_weight_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal currentWeightKg = BigDecimal.ZERO;

    @Column(name = "allowed_material_types", length = 200)
    private String allowedMaterialTypes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public WarehouseBin() {
    }

    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getBinCode() { return binCode; }
    public void setBinCode(String binCode) { this.binCode = binCode; }
    public String getAisle() { return aisle; }
    public void setAisle(String aisle) { this.aisle = aisle; }
    public String getRack() { return rack; }
    public void setRack(String rack) { this.rack = rack; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getBinPosition() { return binPosition; }
    public void setBinPosition(String binPosition) { this.binPosition = binPosition; }
    public WmsEnums.BinType getBinType() { return binType; }
    public void setBinType(WmsEnums.BinType binType) { this.binType = binType; }
    public BigDecimal getMaxWeightKg() { return maxWeightKg; }
    public void setMaxWeightKg(BigDecimal maxWeightKg) { this.maxWeightKg = maxWeightKg; }
    public BigDecimal getMaxVolumeM3() { return maxVolumeM3; }
    public void setMaxVolumeM3(BigDecimal maxVolumeM3) { this.maxVolumeM3 = maxVolumeM3; }
    public BigDecimal getCurrentWeightKg() { return currentWeightKg; }
    public void setCurrentWeightKg(BigDecimal currentWeightKg) { this.currentWeightKg = currentWeightKg; }
    public String getAllowedMaterialTypes() { return allowedMaterialTypes; }
    public void setAllowedMaterialTypes(String allowedMaterialTypes) { this.allowedMaterialTypes = allowedMaterialTypes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
