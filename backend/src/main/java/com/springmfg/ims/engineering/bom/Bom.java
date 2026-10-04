package com.springmfg.ims.engineering.bom;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "bom", schema = "ims")
public class Bom extends BaseEntity {

    @Column(name = "bom_number", nullable = false, length = 20)
    private String bomNumber;

    @Column(name = "revision", nullable = false, length = 5)
    private String revision = "A";

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "status", nullable = false, length = 25)
    private String status = "DRAFT";

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "base_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal baseQuantity = BigDecimal.ONE;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @OneToMany(mappedBy = "bomId", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<BomItem> items = new ArrayList<>();

    public Bom() {}

    public String getBomNumber() { return bomNumber; }
    public void setBomNumber(String v) { this.bomNumber = v; }

    public String getRevision() { return revision; }
    public void setRevision(String v) { this.revision = v; }

    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate v) { this.effectiveFrom = v; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate v) { this.effectiveTo = v; }

    public BigDecimal getBaseQuantity() { return baseQuantity; }
    public void setBaseQuantity(BigDecimal v) { this.baseQuantity = v; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String v) { this.changeReason = v; }

    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long v) { this.approvedBy = v; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant v) { this.approvedAt = v; }

    public List<BomItem> getItems() { return items; }
    public void setItems(List<BomItem> v) { this.items = v; }
}
