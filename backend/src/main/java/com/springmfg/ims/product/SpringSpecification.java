package com.springmfg.ims.product;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The type-specific attributes of one product (JSONB), validated against {@link AttributeDefinition}s. */
@Entity
@Table(name = "spring_specifications")
public class SpringSpecification {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", nullable = false)
    private Map<String, Object> attributes = new LinkedHashMap<>();

    protected SpringSpecification() {
    }

    public SpringSpecification(Long productId, Map<String, Object> attributes) {
        this.productId = productId;
        this.attributes = new LinkedHashMap<>(attributes);
    }

    public Long getProductId() {
        return productId;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = new LinkedHashMap<>(attributes);
    }
}
