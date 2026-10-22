package com.mfg.ims.enterprise.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "plants", schema = "ims")
public class Plant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_code", nullable = false, unique = true, length = 10)
    private String plantCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column
    private String address;

    @Column(nullable = false)
    private Boolean active = true;

    public Long getId() { return id; }
    public String getPlantCode() { return plantCode; }
    public String getName() { return name; }
    public Long getCompanyId() { return companyId; }
    public String getAddress() { return address; }
    public Boolean getActive() { return active; }

    public void setPlantCode(String plantCode) { this.plantCode = plantCode; }
    public void setName(String name) { this.name = name; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public void setAddress(String address) { this.address = address; }
    public void setActive(Boolean active) { this.active = active; }
}
