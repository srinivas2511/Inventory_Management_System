package com.mfg.ims.wms.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.mfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "pick_wave_orders", schema = "ims")
public class PickWaveOrder extends BaseEntity {

    @Column(name = "wave_id", nullable = false)
    private Long waveId;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    public PickWaveOrder() {
    }

    public Long getWaveId() { return waveId; }
    public void setWaveId(Long waveId) { this.waveId = waveId; }
    public Long getSalesOrderId() { return salesOrderId; }
    public void setSalesOrderId(Long salesOrderId) { this.salesOrderId = salesOrderId; }
}
