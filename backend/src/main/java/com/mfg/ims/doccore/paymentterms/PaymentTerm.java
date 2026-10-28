package com.mfg.ims.doccore.paymentterms;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "payment_terms", schema = "ims")
public class PaymentTerm extends BaseEntity {

    @Column(name = "term_code", nullable = false, length = 20)
    private String termCode;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "net_days", nullable = false)
    private int netDays = 0;

    @Column(name = "discount_pct", precision = 9, scale = 4)
    private BigDecimal discountPct;

    @Column(name = "discount_days")
    private Integer discountDays;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public PaymentTerm() {}

    public String getTermCode() { return termCode; }
    public void setTermCode(String v) { this.termCode = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public int getNetDays() { return netDays; }
    public void setNetDays(int v) { this.netDays = v; }
    public BigDecimal getDiscountPct() { return discountPct; }
    public void setDiscountPct(BigDecimal v) { this.discountPct = v; }
    public Integer getDiscountDays() { return discountDays; }
    public void setDiscountDays(Integer v) { this.discountDays = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
