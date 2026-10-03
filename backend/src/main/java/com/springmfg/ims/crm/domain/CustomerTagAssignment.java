package com.springmfg.ims.crm.domain;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_tag_assignments", schema = "ims")
public class CustomerTagAssignment extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    public CustomerTagAssignment() {}

    public CustomerTagAssignment(Long customerId, Long tagId) {
        this.customerId = customerId;
        this.tagId = tagId;
    }

    public Long getCustomerId() { return customerId; }
    public Long getTagId() { return tagId; }
}
