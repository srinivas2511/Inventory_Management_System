package com.mfg.ims.production.jobwork;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "job_work_charges", schema = "ims")
public class JobWorkCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jwo_id", nullable = false)
    private Long jwoId;

    @Column(name = "charge_desc", nullable = false, length = 300)
    private String chargeDesc;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "charge_date", nullable = false)
    private LocalDate chargeDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public Long getId() { return id; }
    public Long getJwoId() { return jwoId; }
    public void setJwoId(Long jwoId) { this.jwoId = jwoId; }
    public String getChargeDesc() { return chargeDesc; }
    public void setChargeDesc(String chargeDesc) { this.chargeDesc = chargeDesc; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getChargeDate() { return chargeDate; }
    public void setChargeDate(LocalDate chargeDate) { this.chargeDate = chargeDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
