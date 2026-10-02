package com.springmfg.ims.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "spring_attribute_definitions", schema = "ims",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_attr_def_type_code",
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

    @Column(name = "data_type", nullable = false, length = 15)
    private String dataType;

    @Column(name = "input_type", nullable = false, length = 20)
    private String inputType;

    @Column(length = 200)
    private String options;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected SpringAttributeDefinition() {}

    public Long getId() { return id; }
    public String getSpringType() { return springType; }
    public String getAttributeCode() { return attributeCode; }
    public String getLabel() { return label; }
    public String getDataType() { return dataType; }
    public String getInputType() { return inputType; }
    public String getOptions() { return options; }
    public boolean isRequired() { return required; }
    public int getSortOrder() { return sortOrder; }
}
