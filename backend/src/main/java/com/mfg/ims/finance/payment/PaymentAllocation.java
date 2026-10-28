package com.mfg.ims.finance.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_allocations", schema = "ims")
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "open_item_id", nullable = false)
    private Long openItemId;

    @Column(name = "allocated", nullable = false, precision = 18, scale = 4)
    private BigDecimal allocated;

    @Column(name = "allocated_inr", nullable = false, precision = 18, scale = 4)
    private BigDecimal allocatedInr;

    @Column(name = "forex_diff_inr", nullable = false, precision = 18, scale = 4)
    private BigDecimal forexDiffInr = BigDecimal.ZERO;

    @Column(name = "allocated_at", nullable = false)
    private Instant allocatedAt = Instant.now();

    @Column(name = "allocated_by")
    private Long allocatedBy;

    @Column(name = "unallocated_at")
    private Instant unallocatedAt;

    public Long getId() { return id; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment v) { this.payment = v; }
    public Long getOpenItemId() { return openItemId; }
    public void setOpenItemId(Long v) { this.openItemId = v; }
    public BigDecimal getAllocated() { return allocated; }
    public void setAllocated(BigDecimal v) { this.allocated = v; }
    public BigDecimal getAllocatedInr() { return allocatedInr; }
    public void setAllocatedInr(BigDecimal v) { this.allocatedInr = v; }
    public BigDecimal getForexDiffInr() { return forexDiffInr; }
    public void setForexDiffInr(BigDecimal v) { this.forexDiffInr = v; }
    public Instant getAllocatedAt() { return allocatedAt; }
    public void setAllocatedAt(Instant v) { this.allocatedAt = v; }
    public Long getAllocatedBy() { return allocatedBy; }
    public void setAllocatedBy(Long v) { this.allocatedBy = v; }
    public Instant getUnallocatedAt() { return unallocatedAt; }
    public void setUnallocatedAt(Instant v) { this.unallocatedAt = v; }
}
