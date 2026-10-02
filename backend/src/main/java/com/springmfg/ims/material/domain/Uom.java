package com.springmfg.ims.material.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "uoms", schema = "ims")
public class Uom {

    @Id
    @Column(length = 10)
    private String code;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false, length = 10)
    private String kind;

    protected Uom() {}

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getKind() { return kind; }
}
