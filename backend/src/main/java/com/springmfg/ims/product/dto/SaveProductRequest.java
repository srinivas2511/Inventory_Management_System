package com.springmfg.ims.product.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveProductRequest(
        @NotBlank @Size(max = 30) String productCode,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = "COMPRESSION|EXTENSION|TORSION|CONICAL|BELLEVILLE|WIRE_FORM|CUSTOM")
        String springType,
        Long primaryMaterialId,
        BigDecimal wireDiameter,
        BigDecimal outerDiameter,
        BigDecimal innerDiameter,
        BigDecimal freeLength,
        BigDecimal numberOfCoils,
        BigDecimal activeCoils,
        BigDecimal springRate,
        BigDecimal maxLoad,
        BigDecimal minLoad,
        BigDecimal workingLength,
        BigDecimal solidHeight,
        @Size(max = 30) String endType,
        @Size(max = 60) String surfaceTreatment,
        @Size(max = 60) String heatTreatment,
        @Size(max = 60) String tolerance,
        BigDecimal unitWeightKg,
        @Size(max = 10) String uom,
        @Size(max = 40) String drawingNumber,
        @Size(max = 10) String drawingRevision,
        Long customerId,
        BigDecimal reorderLevel,
        BigDecimal standardCost,
        JsonNode attributes,
        Long version) {
}
