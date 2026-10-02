package com.springmfg.ims.finance.gl.domain;

import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "gl_accounts", schema = "ims")
public class GlAccount extends BaseEntity {

    @Column(name = "account_code", nullable = false, unique = true, length = 15)
    private String accountCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "account_group_id", nullable = false)
    private Long accountGroupId;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    @Column(name = "normal_balance", nullable = false, length = 6)
    private String normalBalance;

    @Column(name = "allow_direct_posting", nullable = false)
    private Boolean allowDirectPosting = true;

    @Column(name = "is_reconciliation_account", nullable = false)
    private Boolean isReconciliationAccount = false;

    @Column(name = "is_tax_account", nullable = false)
    private Boolean isTaxAccount = false;

    @Column(name = "tax_code", length = 10)
    private String taxCode;

    @Column(nullable = false)
    private Boolean active = true;

    public String getAccountCode() { return accountCode; }
    public String getName() { return name; }
    public Long getAccountGroupId() { return accountGroupId; }
    public String getAccountType() { return accountType; }
    public String getNormalBalance() { return normalBalance; }
    public Boolean getAllowDirectPosting() { return allowDirectPosting; }
    public Boolean getIsReconciliationAccount() { return isReconciliationAccount; }
    public Boolean getIsTaxAccount() { return isTaxAccount; }
    public String getTaxCode() { return taxCode; }
    public Boolean getActive() { return active; }

    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }
    public void setName(String name) { this.name = name; }
    public void setAccountGroupId(Long accountGroupId) { this.accountGroupId = accountGroupId; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public void setNormalBalance(String normalBalance) { this.normalBalance = normalBalance; }
    public void setAllowDirectPosting(Boolean allowDirectPosting) { this.allowDirectPosting = allowDirectPosting; }
    public void setIsReconciliationAccount(Boolean isReconciliationAccount) { this.isReconciliationAccount = isReconciliationAccount; }
    public void setIsTaxAccount(Boolean isTaxAccount) { this.isTaxAccount = isTaxAccount; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    public void setActive(Boolean active) { this.active = active; }
}
