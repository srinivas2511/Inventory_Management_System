package com.springmfg.ims.product;

import java.math.BigDecimal;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One row of the attribute catalogue: what a spring type's product form shows and what the validator enforces. */
@Entity
@Table(name = "spring_attribute_definitions")
public class AttributeDefinition {

    public enum DataType {
        NUMBER, TEXT, ENUM, BOOLEAN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "spring_type", nullable = false)
    private SpringType springType;

    @Column(name = "attribute_code", nullable = false)
    private String attributeCode;

    @Column(name = "label", nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private DataType dataType;

    @Column(name = "unit")
    private String unit;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "min_value")
    private BigDecimal minValue;

    @Column(name = "max_value")
    private BigDecimal maxValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enum_values")
    private List<String> enumValues;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected AttributeDefinition() {
    }

    public Long getId() {
        return id;
    }

    public SpringType getSpringType() {
        return springType;
    }

    public String getAttributeCode() {
        return attributeCode;
    }

    public String getLabel() {
        return label;
    }

    public DataType getDataType() {
        return dataType;
    }

    public String getUnit() {
        return unit;
    }

    public boolean isRequired() {
        return required;
    }

    public BigDecimal getMinValue() {
        return minValue;
    }

    public BigDecimal getMaxValue() {
        return maxValue;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
