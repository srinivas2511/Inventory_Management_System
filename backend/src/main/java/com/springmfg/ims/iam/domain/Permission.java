package com.springmfg.ims.iam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions", schema = "ims")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 30)
    private String module;

    @Column(length = 200)
    private String description;

    protected Permission() {}

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getModule() { return module; }
    public String getDescription() { return description; }
}
