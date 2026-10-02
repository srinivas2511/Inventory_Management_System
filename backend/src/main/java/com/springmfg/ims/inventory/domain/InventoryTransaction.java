package com.springmfg.ims.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only ledger row. Never update or delete (enforced by a DB trigger and ArchUnit).
 */
@Entity
@Table(name = "inventory_transactions", schema = "ims")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_number", nullable = false, unique = true, length = 25)
    private String transactionNumber;

    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate = Instant.now();

    @Column(name = "transaction_type", nullable = false, length = 25)
    private String transactionType;

    @Column(name = "item_type", nullable = false, length = 10)
    private String itemType;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "material_batch_id")
    private Long materialBatchId;

    @Column(name = "fg_batch_id")
    private Long fgBatchId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "stock_status", nullable = false, length = 20)
    private String stockStatus;

    /** Signed: positive = in, negative = out. */
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, length = 10)
    private String uom;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "total_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "reference_type", nullable = false, length = 30)
    private String referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "reference_number", length = 30)
    private String referenceNumber;

    @Column(name = "transfer_group_id")
    private Long transferGroupId;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 3)
    private BigDecimal balanceAfter;

    @Column(length = 500)
    private String remarks;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public InventoryTransaction() {}

    public Long getId() { return id; }
    public String getTransactionNumber() { return transactionNumber; }
    public Instant getTransactionDate() { return transactionDate; }
    public String getTransactionType() { return transactionType; }
    public String getItemType() { return itemType; }
    public Long getMaterialId() { return materialId; }
    public Long getProductId() { return productId; }
    public Long getMaterialBatchId() { return materialBatchId; }
    public Long getFgBatchId() { return fgBatchId; }
    public Long getWarehouseId() { return warehouseId; }
    public Long getLocationId() { return locationId; }
    public String getStockStatus() { return stockStatus; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUom() { return uom; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getTotalCost() { return totalCost; }
    public String getReferenceType() { return referenceType; }
    public Long getReferenceId() { return referenceId; }
    public String getReferenceNumber() { return referenceNumber; }
    public Long getTransferGroupId() { return transferGroupId; }
    public Long getReversalOfId() { return reversalOfId; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getRemarks() { return remarks; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTransactionNumber(String transactionNumber) { this.transactionNumber = transactionNumber; }
    public void setTransactionDate(Instant transactionDate) { this.transactionDate = transactionDate; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setMaterialBatchId(Long materialBatchId) { this.materialBatchId = materialBatchId; }
    public void setFgBatchId(Long fgBatchId) { this.fgBatchId = fgBatchId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public void setUom(String uom) { this.uom = uom; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public void setTransferGroupId(Long transferGroupId) { this.transferGroupId = transferGroupId; }
    public void setReversalOfId(Long reversalOfId) { this.reversalOfId = reversalOfId; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
