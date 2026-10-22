package com.mfg.ims.mrp.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "mrp_proposals", schema = "ims")
public class MrpProposal extends BaseEntity {

    @Column(name = "mrp_run_id", nullable = false)
    private Long mrpRunId;

    @Column(name = "proposal_type", nullable = false, length = 12)
    private String proposalType;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "bom_id")
    private Long bomId;

    @Column(length = 10)
    private String uom;

    @Column(name = "required_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal requiredQty;

    @Column(name = "proposed_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal proposedQty;

    @Column(name = "need_date", nullable = false)
    private LocalDate needDate;

    @Column(name = "proposed_order_date", nullable = false)
    private LocalDate proposedOrderDate;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @Column(name = "lead_time_adjusted", nullable = false)
    private boolean leadTimeAdjusted;

    @Column(name = "source_demand_type", length = 20)
    private String sourceDemandType;

    @Column(name = "source_demand_id")
    private Long sourceDemandId;

    @Column(name = "lot_size_rule", length = 10)
    private String lotSizeRule;

    @Column(name = "machine_hours_required", precision = 18, scale = 3)
    private BigDecimal machineHoursRequired;

    @Column(name = "capacity_overload", nullable = false)
    private boolean capacityOverload;

    @Column(nullable = false, length = 10)
    private String status = "OPEN";

    @Column(name = "converted_document_type", length = 20)
    private String convertedDocumentType;

    @Column(name = "converted_document_id")
    private Long convertedDocumentId;

    @Column(name = "converted_by")
    private Long convertedBy;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "discard_reason", length = 300)
    private String discardReason;

    public Long getMrpRunId() { return mrpRunId; }
    public String getProposalType() { return proposalType; }
    public Long getMaterialId() { return materialId; }
    public Long getProductId() { return productId; }
    public Long getBomId() { return bomId; }
    public String getUom() { return uom; }
    public BigDecimal getRequiredQty() { return requiredQty; }
    public BigDecimal getProposedQty() { return proposedQty; }
    public LocalDate getNeedDate() { return needDate; }
    public LocalDate getProposedOrderDate() { return proposedOrderDate; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public boolean isLeadTimeAdjusted() { return leadTimeAdjusted; }
    public String getSourceDemandType() { return sourceDemandType; }
    public Long getSourceDemandId() { return sourceDemandId; }
    public String getLotSizeRule() { return lotSizeRule; }
    public BigDecimal getMachineHoursRequired() { return machineHoursRequired; }
    public boolean isCapacityOverload() { return capacityOverload; }
    public String getStatus() { return status; }
    public String getConvertedDocumentType() { return convertedDocumentType; }
    public Long getConvertedDocumentId() { return convertedDocumentId; }
    public Long getConvertedBy() { return convertedBy; }
    public Instant getConvertedAt() { return convertedAt; }
    public String getDiscardReason() { return discardReason; }

    public void setStatus(String v) { this.status = v; }
    public void setConvertedDocumentType(String v) { this.convertedDocumentType = v; }
    public void setConvertedDocumentId(Long v) { this.convertedDocumentId = v; }
    public void setConvertedBy(Long v) { this.convertedBy = v; }
    public void setConvertedAt(Instant v) { this.convertedAt = v; }
    public void setDiscardReason(String v) { this.discardReason = v; }
}
