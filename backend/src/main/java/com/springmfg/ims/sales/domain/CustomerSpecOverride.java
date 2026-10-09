package com.springmfg.ims.sales.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

/**
 * SAL-006 / MDM-012: one tolerance override for a customer's product, keyed by the inspection parameter name. During
 * final and in-process inspection of an order made for that customer, the override replaces the standard limits.
 */
@Entity
@Table(name = "customer_spec_overrides", schema = "ims")
public class CustomerSpecOverride extends BaseEntity {

    @Column(name = "customer_product_spec_id", nullable = false)
    private Long customerProductSpecId;

    @Column(name = "parameter_name", nullable = false, length = 60)
    private String parameterName;

    @Column(name = "lower_limit", precision = 14, scale = 4)
    private BigDecimal lowerLimit;

    @Column(name = "upper_limit", precision = 14, scale = 4)
    private BigDecimal upperLimit;

    @Column(precision = 14, scale = 4)
    private BigDecimal nominal;

    @Column(nullable = false)
    private boolean active = true;

    public CustomerSpecOverride() {}

    public Long getCustomerProductSpecId() { return customerProductSpecId; }
    public void setCustomerProductSpecId(Long v) { this.customerProductSpecId = v; }
    public String getParameterName() { return parameterName; }
    public void setParameterName(String v) { this.parameterName = v; }
    public BigDecimal getLowerLimit() { return lowerLimit; }
    public void setLowerLimit(BigDecimal v) { this.lowerLimit = v; }
    public BigDecimal getUpperLimit() { return upperLimit; }
    public void setUpperLimit(BigDecimal v) { this.upperLimit = v; }
    public BigDecimal getNominal() { return nominal; }
    public void setNominal(BigDecimal v) { this.nominal = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
