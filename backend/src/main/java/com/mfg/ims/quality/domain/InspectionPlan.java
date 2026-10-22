package com.mfg.ims.quality.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "inspection_plans", schema = "ims")
public class InspectionPlan extends BaseEntity {

    @Column(name = "plan_code", unique = true, length = 20)
    private String planCode;

    @Column(name = "inspection_type", nullable = false, length = 12)
    private String inspectionType;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq")
    private List<InspectionPlanParameter> parameters = new ArrayList<>();

    public String getPlanCode() { return planCode; }
    public void setPlanCode(String v) { this.planCode = v; }

    public String getInspectionType() { return inspectionType; }
    public void setInspectionType(String v) { this.inspectionType = v; }

    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }

    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }

    public List<InspectionPlanParameter> getParameters() { return parameters; }
}
