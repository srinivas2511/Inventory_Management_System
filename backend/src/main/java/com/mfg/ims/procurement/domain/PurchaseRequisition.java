package com.mfg.ims.procurement.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_requisitions", schema = "ims")
public class PurchaseRequisition extends BaseEntity {

    @Column(name = "pr_number", nullable = false, unique = true, length = 20)
    private String prNumber;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "requested_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal requestedQty;

    @Column(name = "uom", nullable = false, length = 10)
    private String uom;

    @Column(name = "needed_by")
    private LocalDate neededBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "status", nullable = false, length = 15)
    private String status = "OPEN";

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "converted_po_id")
    private Long convertedPoId;

    public String getPrNumber() { return prNumber; }
    public void setPrNumber(String v) { this.prNumber = v; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }

    public BigDecimal getRequestedQty() { return requestedQty; }
    public void setRequestedQty(BigDecimal v) { this.requestedQty = v; }

    public String getUom() { return uom; }
    public void setUom(String v) { this.uom = v; }

    public LocalDate getNeededBy() { return neededBy; }
    public void setNeededBy(LocalDate v) { this.neededBy = v; }

    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long v) { this.requestedBy = v; }

    public Long getConvertedPoId() { return convertedPoId; }
    public void setConvertedPoId(Long v) { this.convertedPoId = v; }
}
