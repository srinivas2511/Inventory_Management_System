package com.mfg.ims.finance.tax.domain;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** The tax of one document, split by tax code, as determined when the document was created. */
@Entity
@Table(name = "invoice_tax_lines", schema = "ims")
public class InvoiceTaxLine extends BaseEntity {

    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false, length = 8)
    private String direction;

    @Column(name = "supply_type", nullable = false, length = 5)
    private String supplyType;

    @Column(name = "tax_code_id", nullable = false)
    private Long taxCodeId;

    @Column(name = "tax_code", nullable = false, length = 15)
    private String taxCode;

    @Column(name = "tax_type", nullable = false, length = 10)
    private String taxType;

    @Column(name = "rate_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePct;

    @Column(name = "taxable_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxableAmount;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "gl_account_id")
    private Long glAccountId;

    public String getDocumentType() { return documentType; }
    public Long getDocumentId() { return documentId; }
    public String getDirection() { return direction; }
    public String getSupplyType() { return supplyType; }
    public Long getTaxCodeId() { return taxCodeId; }
    public String getTaxCode() { return taxCode; }
    public String getTaxType() { return taxType; }
    public BigDecimal getRatePct() { return ratePct; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public Long getGlAccountId() { return glAccountId; }

    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public void setDirection(String direction) { this.direction = direction; }
    public void setSupplyType(String supplyType) { this.supplyType = supplyType; }
    public void setTaxCodeId(Long taxCodeId) { this.taxCodeId = taxCodeId; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    public void setTaxType(String taxType) { this.taxType = taxType; }
    public void setRatePct(BigDecimal ratePct) { this.ratePct = ratePct; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public void setGlAccountId(Long glAccountId) { this.glAccountId = glAccountId; }
}
