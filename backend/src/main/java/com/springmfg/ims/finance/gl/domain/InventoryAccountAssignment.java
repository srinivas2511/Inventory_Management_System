package com.springmfg.ims.finance.gl.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_account_assignments", schema = "ims")
public class InventoryAccountAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    @Column(name = "transaction_type", nullable = false, length = 25)
    private String transactionType;

    @Column(name = "item_type", length = 10)
    private String itemType;

    @Column(name = "valuation_class", length = 20)
    private String valuationClass;

    @Column(name = "gl_account_debit_id", nullable = false)
    private Long glAccountDebitId;

    @Column(name = "gl_account_credit_id", nullable = false)
    private Long glAccountCreditId;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getTransactionType() { return transactionType; }
    public String getItemType() { return itemType; }
    public String getValuationClass() { return valuationClass; }
    public Long getGlAccountDebitId() { return glAccountDebitId; }
    public Long getGlAccountCreditId() { return glAccountCreditId; }

    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public void setValuationClass(String valuationClass) { this.valuationClass = valuationClass; }
    public void setGlAccountDebitId(Long glAccountDebitId) { this.glAccountDebitId = glAccountDebitId; }
    public void setGlAccountCreditId(Long glAccountCreditId) { this.glAccountCreditId = glAccountCreditId; }
}
