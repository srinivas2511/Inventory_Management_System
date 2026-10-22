package com.mfg.ims.qualityplus.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A control chart for one parameter of one product (QMS-007); limits are derived from data, spec limits are entered. */
@Entity
@Table(name = "spc_control_charts", schema = "ims")
public class SpcControlChart extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "parameter_name", nullable = false, length = 60)
    private String parameterName;

    @Enumerated(EnumType.STRING)
    @Column(name = "chart_type", nullable = false, length = 10)
    private SpcChartType chartType;

    @Column(name = "subgroup_size", nullable = false)
    private int subgroupSize = 5;

    @Column(name = "cl")
    private BigDecimal cl;

    @Column(name = "ucl")
    private BigDecimal ucl;

    @Column(name = "lcl")
    private BigDecimal lcl;

    @Column(name = "cl_r")
    private BigDecimal clR;

    @Column(name = "ucl_r")
    private BigDecimal uclR;

    @Column(name = "lcl_r")
    private BigDecimal lclR;

    @Column(name = "usl")
    private BigDecimal usl;

    @Column(name = "lsl")
    private BigDecimal lsl;

    @Column(name = "limits_established_at")
    private Instant limitsEstablishedAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }

    public String getParameterName() { return parameterName; }
    public void setParameterName(String v) { this.parameterName = v; }

    public SpcChartType getChartType() { return chartType; }
    public void setChartType(SpcChartType v) { this.chartType = v; }

    public int getSubgroupSize() { return subgroupSize; }
    public void setSubgroupSize(int v) { this.subgroupSize = v; }

    public BigDecimal getCl() { return cl; }
    public void setCl(BigDecimal v) { this.cl = v; }

    public BigDecimal getUcl() { return ucl; }
    public void setUcl(BigDecimal v) { this.ucl = v; }

    public BigDecimal getLcl() { return lcl; }
    public void setLcl(BigDecimal v) { this.lcl = v; }

    public BigDecimal getClR() { return clR; }
    public void setClR(BigDecimal v) { this.clR = v; }

    public BigDecimal getUclR() { return uclR; }
    public void setUclR(BigDecimal v) { this.uclR = v; }

    public BigDecimal getLclR() { return lclR; }
    public void setLclR(BigDecimal v) { this.lclR = v; }

    public BigDecimal getUsl() { return usl; }
    public void setUsl(BigDecimal v) { this.usl = v; }

    public BigDecimal getLsl() { return lsl; }
    public void setLsl(BigDecimal v) { this.lsl = v; }

    public Instant getLimitsEstablishedAt() { return limitsEstablishedAt; }
    public void setLimitsEstablishedAt(Instant v) { this.limitsEstablishedAt = v; }

    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
