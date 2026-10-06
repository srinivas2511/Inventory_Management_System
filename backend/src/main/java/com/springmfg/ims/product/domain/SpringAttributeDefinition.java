package com.springmfg.ims.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "spring_attribute_definitions", schema = "ims",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_attr_def",
        columnNames = {"spring_type", "attribute_code"}))
public class SpringAttributeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spring_type", nullable = false, length = 20)
    private String springType;

    @Column(name = "attribute_code", nullable = false, length = 40)
    private String attributeCode;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(name = "data_type", nullable = false, length = 12)
    private String dataType;

    @Column(length = 10)
    private String unit;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "min_value")
    private BigDecimal minValue;

    @Column(name = "max_value")
    private BigDecimal maxValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enum_values")
    private String enumValues;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected SpringAttributeDefinition() {}

    public Long getId() { return id; }
    public String getSpringType() { return springType; }
    public String getAttributeCode() { return attributeCode; }
    public String getLabel() { return label; }
    public String getDataType() { return dataType; }
    public String getUnit() { return unit; }
    public boolean isRequired() { return required; }
    public BigDecimal getMinValue() { return minValue; }
    public BigDecimal getMaxValue() { return maxValue; }
    public String getEnumValues() { return enumValues; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
}
