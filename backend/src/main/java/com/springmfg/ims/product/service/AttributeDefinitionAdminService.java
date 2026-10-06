package com.springmfg.ims.product.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.product.dto.AttributeDefinitionAdminDto;
import com.springmfg.ims.product.dto.SaveAttributeDefinitionRequest;
import com.springmfg.ims.product.repository.ProductTypeRepository;

/**
 * Attribute definitions as data (R20-4.1): add, edit and deactivate the attributes a product type carries without a
 * migration. Writes use JDBC so enum_values is stored as real JSONB, and every change is audited.
 */
@Service
public class AttributeDefinitionAdminService {

    private static final String SELECT = """
            SELECT id, spring_type, attribute_code, label, data_type, unit, required, min_value, max_value,
                   enum_values::text AS enum_values, display_order, active
            FROM ims.spring_attribute_definitions""";

    private final NamedParameterJdbcTemplate jdbc;
    private final ProductTypeRepository typeRepo;
    private final AuditService audit;
    private final CacheManager cacheManager;
    private final ObjectMapper mapper;

    public AttributeDefinitionAdminService(NamedParameterJdbcTemplate jdbc, ProductTypeRepository typeRepo,
            AuditService audit, CacheManager cacheManager, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.typeRepo = typeRepo;
        this.audit = audit;
        this.cacheManager = cacheManager;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<AttributeDefinitionAdminDto> list(String productType) {
        if (productType == null || productType.isBlank()) {
            return jdbc.query(SELECT + " ORDER BY spring_type, display_order, attribute_code", rowMapper());
        }
        return jdbc.query(SELECT + " WHERE spring_type = :t ORDER BY display_order, attribute_code",
                new MapSqlParameterSource("t", productType.trim().toUpperCase()), rowMapper());
    }

    @Transactional
    public AttributeDefinitionAdminDto create(SaveAttributeDefinitionRequest req) {
        String type = req.productType().trim().toUpperCase();
        if (!typeRepo.existsByCode(type)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Unknown product type " + type);
        }
        validate(req);
        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM ims.spring_attribute_definitions WHERE spring_type = :t AND attribute_code = :c",
                new MapSqlParameterSource("t", type).addValue("c", req.attributeCode()), Integer.class);
        if (existing != null && existing > 0) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY,
                    "Attribute " + req.attributeCode() + " already exists for " + type);
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO ims.spring_attribute_definitions
                  (spring_type, attribute_code, label, data_type, unit, required, min_value, max_value, enum_values,
                   display_order)
                VALUES (:t, :c, :l, :d, :u, :r, :min, :max, CAST(:e AS jsonb), :o) RETURNING id""",
                params(req, type), Long.class);
        evictCache();
        AttributeDefinitionAdminDto created = require(id);
        audit.record("ATTRIBUTE_DEFINITION_CREATE", "ATTRIBUTE_DEFINITION", String.valueOf(id), null,
                summary(created), null);
        return created;
    }

    @Transactional
    public AttributeDefinitionAdminDto update(Long id, SaveAttributeDefinitionRequest req) {
        AttributeDefinitionAdminDto before = require(id);
        if (!before.productType().equalsIgnoreCase(req.productType().trim())
                || !before.attributeCode().equals(req.attributeCode())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "The product type and attribute code of an existing definition cannot be changed");
        }
        validate(req);
        jdbc.update("""
                UPDATE ims.spring_attribute_definitions
                SET label = :l, data_type = :d, unit = :u, required = :r, min_value = :min, max_value = :max,
                    enum_values = CAST(:e AS jsonb), display_order = :o
                WHERE id = :id""", params(req, before.productType()).addValue("id", id));
        evictCache();
        AttributeDefinitionAdminDto after = require(id);
        audit.record("ATTRIBUTE_DEFINITION_UPDATE", "ATTRIBUTE_DEFINITION", String.valueOf(id), summary(before),
                summary(after), null);
        return after;
    }

    @Transactional
    public AttributeDefinitionAdminDto setActive(Long id, boolean active) {
        AttributeDefinitionAdminDto before = require(id);
        if (before.active() == active) {
            return before;
        }
        jdbc.update("UPDATE ims.spring_attribute_definitions SET active = :a WHERE id = :id",
                new MapSqlParameterSource("a", active).addValue("id", id));
        evictCache();
        AttributeDefinitionAdminDto after = require(id);
        audit.record(active ? "ATTRIBUTE_DEFINITION_ACTIVATE" : "ATTRIBUTE_DEFINITION_DEACTIVATE",
                "ATTRIBUTE_DEFINITION", String.valueOf(id), summary(before), summary(after), null);
        return after;
    }

    private void validate(SaveAttributeDefinitionRequest req) {
        boolean isEnum = "ENUM".equals(req.dataType());
        boolean hasValues = req.enumValues() != null && !req.enumValues().isEmpty();
        if (isEnum && !hasValues) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "An ENUM attribute needs at least one value");
        }
        if (!isEnum && hasValues) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Only ENUM attributes can list values");
        }
        if (isEnum && req.enumValues().stream().distinct().count() != req.enumValues().size()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "ENUM values must be unique");
        }
        boolean numeric = "NUMBER".equals(req.dataType());
        if (!numeric && (req.minValue() != null || req.maxValue() != null)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Only NUMBER attributes can have limits");
        }
        if (req.minValue() != null && req.maxValue() != null && req.minValue().compareTo(req.maxValue()) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "The minimum cannot exceed the maximum");
        }
    }

    private MapSqlParameterSource params(SaveAttributeDefinitionRequest req, String type) {
        String enumJson = null;
        if (req.enumValues() != null && !req.enumValues().isEmpty()) {
            try {
                enumJson = mapper.writeValueAsString(req.enumValues());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Cannot serialise enum values", e);
            }
        }
        return new MapSqlParameterSource()
                .addValue("t", type)
                .addValue("c", req.attributeCode())
                .addValue("l", req.label())
                .addValue("d", req.dataType())
                .addValue("u", req.unit() == null || req.unit().isBlank() ? null : req.unit().trim())
                .addValue("r", req.required())
                .addValue("min", req.minValue(), java.sql.Types.NUMERIC)
                .addValue("max", req.maxValue(), java.sql.Types.NUMERIC)
                .addValue("e", enumJson, java.sql.Types.VARCHAR)
                .addValue("o", req.displayOrder());
    }

    private AttributeDefinitionAdminDto require(Long id) {
        try {
            return jdbc.queryForObject(SELECT + " WHERE id = :id", new MapSqlParameterSource("id", id), rowMapper());
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("AttributeDefinition", id);
        }
    }

    private RowMapper<AttributeDefinitionAdminDto> rowMapper() {
        return (rs, i) -> new AttributeDefinitionAdminDto(
                rs.getLong("id"), rs.getString("spring_type"), rs.getString("attribute_code"), rs.getString("label"),
                rs.getString("data_type"), rs.getString("unit"), rs.getBoolean("required"),
                rs.getObject("min_value", BigDecimal.class), rs.getObject("max_value", BigDecimal.class),
                parseEnum(rs.getString("enum_values")), rs.getInt("display_order"), rs.getBoolean("active"));
    }

    private List<String> parseEnum(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored enum_values is not a JSON array: " + json, e);
        }
    }

    private static String summary(AttributeDefinitionAdminDto d) {
        return d.productType() + "." + d.attributeCode() + " [" + d.label() + ", " + d.dataType()
                + (d.unit() == null ? "" : " " + d.unit()) + ", required=" + d.required() + ", order="
                + d.displayOrder() + ", active=" + d.active()
                + (d.enumValues().isEmpty() ? "" : ", values=" + d.enumValues()) + "]";
    }

    private void evictCache() {
        Cache cache = cacheManager.getCache("springAttributes");
        if (cache != null) {
            cache.clear();
        }
    }
}
