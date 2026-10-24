package com.mfg.ims.maintenance.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "machine_sensor_readings", schema = "ims")
public class MachineSensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "reading_type", nullable = false, length = 30)
    private String readingType;

    @Column(name = "reading_value", nullable = false)
    private BigDecimal readingValue;

    @Column(name = "unit", length = 10)
    private String unit;

    @Column(name = "read_at", nullable = false)
    private Instant readAt = Instant.now();

    @Column(name = "source", nullable = false, length = 20)
    private String source = "MANUAL";

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    public Long getId() { return id; }
    public Long getMachineId() { return machineId; }
    public void setMachineId(Long machineId) { this.machineId = machineId; }
    public String getReadingType() { return readingType; }
    public void setReadingType(String readingType) { this.readingType = readingType; }
    public BigDecimal getReadingValue() { return readingValue; }
    public void setReadingValue(BigDecimal readingValue) { this.readingValue = readingValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getCompanyId() { return companyId; }
}
