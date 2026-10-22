package com.mfg.ims.finance.gl.domain;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "gl_account_groups", schema = "ims")
public class GlAccountGroup extends BaseEntity {

    @Column(name = "group_code", nullable = false, unique = true, length = 10)
    private String groupCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    public String getGroupCode() { return groupCode; }
    public String getName() { return name; }
    public String getAccountType() { return accountType; }

    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public void setName(String name) { this.name = name; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
}
