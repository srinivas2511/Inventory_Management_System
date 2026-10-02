package com.springmfg.ims.engineering.routing;

import java.math.BigDecimal;

public record ProductRoutingDto(
        Long id,
        Long productId,
        int sequenceNo,
        Long operationId,
        String operationName,
        Long machineId,
        String machineName,
        BigDecimal standardTimeMin,
        BigDecimal setupTimeMin,
        boolean inspectionPoint,
        boolean active) {

    public static ProductRoutingDto from(ProductRouting r, String operationName, String machineName) {
        return new ProductRoutingDto(
                r.getId(),
                r.getProductId(),
                r.getSequenceNo(),
                r.getOperationId(),
                operationName,
                r.getMachineId(),
                machineName,
                r.getStandardTimeMin(),
                r.getSetupTimeMin(),
                r.isInspectionPoint(),
                r.isActive());
    }
}
