package com.mfg.ims.production.jobwork;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "job_work_order_items", schema = "ims")
public class JobWorkOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jwo_id", nullable = false)
    private JobWorkOrder jwo;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "sent_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal sentQty;

    @Column(name = "received_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal receivedQty = BigDecimal.ZERO;

    @Column(name = "scrapped_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal scrappedQty = BigDecimal.ZERO;

    @Column(name = "uom", length = 10)
    private String uom;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private Long createdBy = 1L;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public Long getId() { return id; }
    public JobWorkOrder getJwo() { return jwo; }
    public void setJwo(JobWorkOrder jwo) { this.jwo = jwo; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getSentQty() { return sentQty; }
    public void setSentQty(BigDecimal sentQty) { this.sentQty = sentQty; }
    public BigDecimal getReceivedQty() { return receivedQty; }
    public void setReceivedQty(BigDecimal receivedQty) { this.receivedQty = receivedQty; }
    public BigDecimal getScrappedQty() { return scrappedQty; }
    public void setScrappedQty(BigDecimal scrappedQty) { this.scrappedQty = scrappedQty; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public Long getVersion() { return version; }
}
