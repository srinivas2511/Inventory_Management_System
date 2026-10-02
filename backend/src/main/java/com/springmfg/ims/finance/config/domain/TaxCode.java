package com.springmfg.ims.finance.config.domain;

import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tax_codes", schema = "ims")
public class TaxCode extends BaseEntity {

    @Column(name = "tax_code", nullable = false, unique = true, length = 10)
    private String taxCode;

    @Column(nullable = false, length = 60)
    private String description;

    @Column(name = "tax_type", nullable = false, length = 10)
    private String taxType;

    @Column(name = "rate_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePct;

    @Column(name = "gl_account_id")
    private Long glAccountId;

    private boolean active = true;

    public String getTaxCode() { return taxCode; }
    public String getDescription() { return description; }
    public String getTaxType() { return taxType; }
    public BigDecimal getRatePct() { return ratePct; }
    public Long getGlAccountId() { return glAccountId; }
    public boolean isActive() { return active; }

    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    public void setDescription(String description) { this.description = description; }
    public void setTaxType(String taxType) { this.taxType = taxType; }
    public void setRatePct(BigDecimal ratePct) { this.ratePct = ratePct; }
    public void setGlAccountId(Long glAccountId) { this.glAccountId = glAccountId; }
    public void setActive(boolean active) { this.active = active; }
}
