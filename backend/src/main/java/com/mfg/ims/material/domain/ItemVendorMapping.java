package com.mfg.ims.material.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "item_vendor_mappings", schema = "ims")
public class ItemVendorMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "supplier_item_code", length = 100)
    private String supplierItemCode;

    @Column(name = "supplier_item_name", length = 255)
    private String supplierItemName;

    @Column(name = "last_price", precision = 18, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "moq", nullable = false, precision = 18, scale = 3)
    private BigDecimal moq = BigDecimal.ZERO;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays = 0;

    @Column(name = "preferred", nullable = false)
    private boolean preferred = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ItemVendorMapping() {}

    public Long getId() { return id; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long v) { this.itemId = v; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long v) { this.supplierId = v; }
    public String getSupplierItemCode() { return supplierItemCode; }
    public void setSupplierItemCode(String v) { this.supplierItemCode = v; }
    public String getSupplierItemName() { return supplierItemName; }
    public void setSupplierItemName(String v) { this.supplierItemName = v; }
    public BigDecimal getLastPrice() { return lastPrice; }
    public void setLastPrice(BigDecimal v) { this.lastPrice = v; }
    public BigDecimal getMoq() { return moq; }
    public void setMoq(BigDecimal v) { this.moq = v; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(int v) { this.leadTimeDays = v; }
    public boolean isPreferred() { return preferred; }
    public void setPreferred(boolean v) { this.preferred = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCompanyId() { return companyId; }
}
