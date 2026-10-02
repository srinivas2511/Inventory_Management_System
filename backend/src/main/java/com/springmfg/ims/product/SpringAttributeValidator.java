package com.springmfg.ims.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.springmfg.ims.common.exception.Problems;

/**
 * Validates a product's type-specific values against the attribute catalogue ({@link AttributeDefinition}), which
 * is data, so a new definition row changes validation without code.
 * <p>
 * A definition whose code is a product column ({@link #CORE_CODES}) validates the column value; every other
 * definition validates an entry of {@code specifications}. Rules: numbers are numbers (not text) within the limits,
 * enums are one of the permitted values, text is at most 200 characters, unknown keys are rejected, a column-backed
 * code inside {@code specifications} is rejected (it belongs at the top level). Required attributes are enforced
 * only when {@code complete} is true (activation and edits of ACTIVE products); a DRAFT may be saved unfinished.
 * {@code CUSTOM} springs have no fixed attributes and accept free-form specifications within sane limits.
 */
@Component
class SpringAttributeValidator {

    /** Definition codes stored in product columns. */
    static final Set<String> CORE_CODES = Set.of("wireDiameter", "outerDiameter", "innerDiameter", "freeLength",
            "numberOfCoils", "activeCoils", "springRate", "maxLoad", "minLoad", "workingLength", "solidHeight", "endType");

    static final int MAX_TEXT = 200;
    static final int MAX_FREE_FORM_ENTRIES = 30;
    private static final Pattern FREE_FORM_KEY = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,39}");

    private final AttributeDefinitionRepository definitions;

    SpringAttributeValidator(AttributeDefinitionRepository definitions) {
        this.definitions = definitions;
    }

    /** The catalogue for a type, in display order. */
    List<AttributeDefinition> catalogue(SpringType type) {
        return definitions.findBySpringTypeOrderByDisplayOrderAscAttributeCodeAsc(type);
    }

    static boolean isCore(AttributeDefinition definition) {
        return CORE_CODES.contains(definition.getAttributeCode());
    }

    /**
     * @param core          the column-backed values by code (null for unset)
     * @param specs         the {@code specifications} map as sent (may be null)
     * @param complete      enforce required attributes
     * @return every problem found, empty if the product is acceptable
     */
    List<Problems.FieldError> validate(SpringType type, Map<String, Object> core, Map<String, Object> specs, boolean complete) {
        List<AttributeDefinition> catalogue = catalogue(type);
        Map<String, Object> given = specs == null ? Map.of() : specs;
        List<Problems.FieldError> errors = new ArrayList<>();

        Set<String> specKeys = catalogue.stream().filter(d -> !isCore(d)).map(AttributeDefinition::getAttributeCode).collect(Collectors.toSet());
        for (String key : given.keySet()) {
            if (CORE_CODES.contains(key)) {
                errors.add(error("specifications." + key, "'" + key + "' is a core product field: send it at the top level, not in specifications."));
            } else if (!specKeys.contains(key) && type != SpringType.CUSTOM) {
                errors.add(error("specifications." + key, "Unknown attribute '" + key + "' for " + type + ". Allowed: "
                        + (specKeys.isEmpty() ? "none" : String.join(", ", specKeys.stream().sorted().toList())) + "."));
            }
        }
        if (type == SpringType.CUSTOM) {
            freeForm(given, specKeys, errors);
        }

        for (AttributeDefinition d : catalogue) {
            boolean coreBacked = isCore(d);
            Object value = coreBacked ? core.get(d.getAttributeCode()) : given.get(d.getAttributeCode());
            String field = coreBacked ? d.getAttributeCode() : "specifications." + d.getAttributeCode();
            if (value == null) {
                if (d.isRequired() && complete) {
                    errors.add(error(field, d.getLabel() + " is required."));
                }
                continue;
            }
            String problem = check(d, value);
            if (problem != null) {
                errors.add(error(field, d.getLabel() + ": " + problem));
            }
        }
        return errors;
    }

    /** Converts accepted JSON numbers to {@link BigDecimal} so they are stored exactly as written. */
    static Map<String, Object> normalise(Map<String, Object> specs) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (specs != null) {
            specs.forEach((k, v) -> out.put(k, v instanceof Number n && !(v instanceof BigDecimal) ? new BigDecimal(n.toString()) : v));
        }
        return out;
    }

    private static String check(AttributeDefinition d, Object value) {
        switch (d.getDataType()) {
            case NUMBER -> {
                if (!(value instanceof Number number)) {
                    return "must be a number.";
                }
                BigDecimal n = new BigDecimal(number.toString());
                if (d.getMinValue() != null && n.compareTo(d.getMinValue()) < 0) {
                    return "must be at least " + d.getMinValue().stripTrailingZeros().toPlainString() + unit(d) + ".";
                }
                if (d.getMaxValue() != null && n.compareTo(d.getMaxValue()) > 0) {
                    return "must be at most " + d.getMaxValue().stripTrailingZeros().toPlainString() + unit(d) + ".";
                }
                return null;
            }
            case TEXT -> {
                if (!(value instanceof String text) || text.isBlank()) {
                    return "must be text.";
                }
                return text.length() > MAX_TEXT ? "must be at most " + MAX_TEXT + " characters." : null;
            }
            case ENUM -> {
                List<String> allowed = d.getEnumValues() == null ? List.of() : d.getEnumValues();
                return value instanceof String text && allowed.contains(text) ? null : "must be one of " + String.join(", ", allowed) + ".";
            }
            default -> {
                return value instanceof Boolean ? null : "must be true or false.";
            }
        }
    }

    private static String unit(AttributeDefinition d) {
        return d.getUnit() == null ? "" : " " + d.getUnit();
    }

    /** Free-form (CUSTOM) specifications: simple keys, scalar values, bounded size. */
    private static void freeForm(Map<String, Object> given, Set<String> definedKeys, List<Problems.FieldError> errors) {
        if (given.size() > MAX_FREE_FORM_ENTRIES) {
            errors.add(error("specifications", "At most " + MAX_FREE_FORM_ENTRIES + " custom attributes are allowed."));
        }
        given.forEach((key, value) -> {
            if (CORE_CODES.contains(key) || definedKeys.contains(key)) {
                return; // reported or validated elsewhere
            }
            String field = "specifications." + key;
            if (!FREE_FORM_KEY.matcher(key).matches()) {
                errors.add(error(field, "Attribute names are letters, digits and underscore, starting with a letter (max 40)."));
            } else if (value instanceof String text) {
                if (text.length() > MAX_TEXT) {
                    errors.add(error(field, "Text must be at most " + MAX_TEXT + " characters."));
                }
            } else if (!(value instanceof Number) && !(value instanceof Boolean)) {
                errors.add(error(field, "Must be a number, text or true/false."));
            }
        });
    }

    private static Problems.FieldError error(String field, String message) {
        return new Problems.FieldError(field, message);
    }
}
