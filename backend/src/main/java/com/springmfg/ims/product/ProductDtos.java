package com.springmfg.ims.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.springmfg.ims.masterdata.PartnerDtos.PartnerRef;

/** Request and response shapes of {@code /api/products}. Dimensions have 3 decimals, money 4 (CLAUDE.md rule 8). */
public final class ProductDtos {

    public static final String CODE_PATTERN = "[A-Z0-9][A-Z0-9._-]{1,29}";

    private ProductDtos() {
    }

    /** Compact reference to a material. */
    public record MaterialRef(long id, String code, String name) {
    }

    public record ProductSummary(long id, String code, String name, SpringType springType, MaterialRef primaryMaterial,
            java.math.BigDecimal wireDiameter, java.math.BigDecimal outerDiameter, java.math.BigDecimal freeLength,
            String drawingNumber, String drawingRevision, PartnerRef customer, ProductStatus status, boolean active, List<String> allowedActions) {
    }

    public record ProductResponse(long id, String code, String name, SpringType springType, MaterialRef primaryMaterial,
            BigDecimal wireDiameter, BigDecimal outerDiameter, BigDecimal innerDiameter, BigDecimal freeLength,
            BigDecimal numberOfCoils, BigDecimal activeCoils, BigDecimal springRate, BigDecimal maxLoad,
            BigDecimal minLoad, BigDecimal workingLength, BigDecimal solidHeight, String endType,
            String surfaceTreatment, String heatTreatment, String tolerance, BigDecimal unitWeightKg, String uom,
            String drawingNumber, String drawingRevision, PartnerRef customer, ProductStatus status,
            BigDecimal reorderLevel, BigDecimal standardCost, boolean active, Map<String, Object> specifications,
            List<String> allowedActions, long version, Instant createdAt, Instant updatedAt) {
    }

    /**
     * The body of {@code POST} (create) and {@code PUT} (replace). {@code productCode} is required on create and may
     * be omitted (or repeated unchanged) on update; {@code version} is required on update and ignored on create.
     * {@code specifications} holds only the attributes that are <em>not</em> product columns (see
     * {@code GET /api/spring-types/{type}/attributes}, field {@code storage}).
     */
    public record ProductRequest(
            @Size(max = 30) String productCode,
            @NotBlank @Size(max = 150) String name,
            @NotNull SpringType springType,
            Long primaryMaterialId,
            @DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal wireDiameter,
            @DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal outerDiameter,
            @DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal innerDiameter,
            @DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal freeLength,
            @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal numberOfCoils,
            @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal activeCoils,
            @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal springRate,
            @DecimalMin("0") @Digits(integer = 9, fraction = 3) BigDecimal maxLoad,
            @DecimalMin("0") @Digits(integer = 9, fraction = 3) BigDecimal minLoad,
            @DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal workingLength,
            @DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal solidHeight,
            @Size(max = 30) String endType,
            @Size(max = 60) String surfaceTreatment,
            @Size(max = 60) String heatTreatment,
            @Size(max = 60) String tolerance,
            @DecimalMin("0") @Digits(integer = 6, fraction = 6) BigDecimal unitWeightKg,
            @Size(max = 10) String uom,
            @Size(max = 40) String drawingNumber,
            @Size(max = 10) String drawingRevision,
            Long customerId,
            @DecimalMin("0") @Digits(integer = 15, fraction = 3) BigDecimal reorderLevel,
            @DecimalMin("0") @Digits(integer = 14, fraction = 4) BigDecimal standardCost,
            Map<String, Object> specifications,
            Long version) {
    }

    /** One entry of the catalogue for a spring type. {@code storage} says where the value goes in a product request. */
    public record AttributeResponse(String code, String label, String dataType, String unit, boolean required,
            BigDecimal minValue, BigDecimal maxValue, List<String> enumValues, int displayOrder, String storage) {
    }

    public record SpringTypeResponse(SpringType type, String label, int attributeCount, boolean freeForm) {
    }
}
