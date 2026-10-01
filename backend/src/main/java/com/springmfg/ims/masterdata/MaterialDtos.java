package com.springmfg.ims.masterdata;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Request and response shapes of {@code /api/materials}. Quantities have 3 decimals, money 4 (CLAUDE.md rule 8). */
public final class MaterialDtos {

    public static final String CODE_PATTERN = "[A-Z0-9][A-Z0-9._-]{1,29}";

    private MaterialDtos() {
    }

    public record MaterialSummary(long id, String code, String name, MaterialType materialType, String grade,
            BigDecimal diameterMm, String uom, PartnerDtos.PartnerRef preferredSupplier, BigDecimal minStock,
            BigDecimal reorderLevel, BigDecimal maxStock, BigDecimal standardCost, boolean active) {
    }

    public record MaterialResponse(long id, String code, String name, MaterialType materialType, String grade,
            BigDecimal diameterMm, String uom, PartnerDtos.PartnerRef preferredSupplier, BigDecimal minStock,
            BigDecimal reorderLevel, BigDecimal maxStock, BigDecimal standardCost, Integer shelfLifeDays,
            String description, boolean active, long version, Instant createdAt, Instant updatedAt) {
    }

    public record CreateMaterialRequest(
            @NotBlank @Pattern(regexp = CODE_PATTERN, message = "2-30 characters: A-Z, 0-9, dot, underscore or hyphen, upper case") String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull MaterialType materialType,
            @Size(max = 30) String grade,
            @DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal diameterMm,
            @NotBlank @Size(max = 10) String uom,
            Long preferredSupplierId,
            @DecimalMin("0") @Digits(integer = 15, fraction = 3) BigDecimal minStock,
            @DecimalMin("0") @Digits(integer = 15, fraction = 3) BigDecimal reorderLevel,
            @DecimalMin("0") @Digits(integer = 15, fraction = 3) BigDecimal maxStock,
            @DecimalMin("0") @Digits(integer = 14, fraction = 4) BigDecimal standardCost,
            @Positive Integer shelfLifeDays,
            @Size(max = 500) String description) {
    }

    /** Full replacement of the editable fields; the code never changes. */
    public record UpdateMaterialRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull MaterialType materialType,
            @Size(max = 30) String grade,
            @DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal diameterMm,
            @NotBlank @Size(max = 10) String uom,
            Long preferredSupplierId,
            @DecimalMin("0") @Digits(integer = 15, fraction = 3) BigDecimal minStock,
            @DecimalMin("0") @Digits(integer = 15, fraction = 3) BigDecimal reorderLevel,
            @DecimalMin("0") @Digits(integer = 15, fraction = 3) BigDecimal maxStock,
            @DecimalMin("0") @Digits(integer = 14, fraction = 4) BigDecimal standardCost,
            @Positive Integer shelfLifeDays,
            @Size(max = 500) String description,
            @NotNull Long version) {
    }

    public record UomResponse(String code, String name, String kind) {
    }

}
