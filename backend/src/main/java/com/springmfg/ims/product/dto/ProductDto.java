package com.springmfg.ims.product.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.product.domain.Product;

public record ProductDto(
        Long id,
        String productCode,
        String name,
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
        String endType,
        String surfaceTreatment,
        String heatTreatment,
        String tolerance,
        BigDecimal unitWeightKg,
        String uom,
        String drawingNumber,
        String drawingRevision,
        Long customerId,
        String status,
        BigDecimal reorderLevel,
        BigDecimal standardCost,
        boolean active,
        JsonNode attributes,
        Long version) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ProductDto from(Product p) {
        JsonNode attrs = null;
        if (p.getSpecification() != null) {
            try { attrs = MAPPER.readTree(p.getSpecification().getAttributes()); }
            catch (Exception ignored) {}
        }
        return new ProductDto(
            p.getId(), p.getProductCode(), p.getName(), p.getSpringType(),
            p.getPrimaryMaterial() != null ? p.getPrimaryMaterial().getId() : null,
            p.getWireDiameter(), p.getOuterDiameter(), p.getInnerDiameter(),
            p.getFreeLength(), p.getNumberOfCoils(), p.getActiveCoils(),
            p.getSpringRate(), p.getMaxLoad(), p.getMinLoad(),
            p.getWorkingLength(), p.getSolidHeight(), p.getEndType(),
            p.getSurfaceTreatment(), p.getHeatTreatment(), p.getTolerance(),
            p.getUnitWeightKg(), p.getUom(), p.getDrawingNumber(), p.getDrawingRevision(),
            p.getCustomer() != null ? p.getCustomer().getId() : null,
            p.getStatus(), p.getReorderLevel(), p.getStandardCost(),
            p.isActive(), attrs, p.getVersion());
    }
}
