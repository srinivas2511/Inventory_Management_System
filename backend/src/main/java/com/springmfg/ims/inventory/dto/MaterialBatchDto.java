package com.springmfg.ims.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.springmfg.ims.inventory.domain.MaterialBatch;

public record MaterialBatchDto(
        Long id,
        String batchNumber,
        Long materialId,
        String materialCode,
        Long supplierId,
        String supplierName,
        Long purchaseOrderId,
        LocalDate manufacturingDate,
        LocalDate receivedDate,
        LocalDate expiryDate,
        BigDecimal receivedQty,
        BigDecimal remainingQty,
        String heatNumber,
        String certificateNumber,
        BigDecimal unitCost,
        String status) {

    public static MaterialBatchDto from(MaterialBatch b) {
        return new MaterialBatchDto(
                b.getId(), b.getBatchNumber(),
                b.getMaterial().getId(), b.getMaterial().getMaterialCode(),
                b.getSupplier() != null ? b.getSupplier().getId() : null,
                b.getSupplier() != null ? b.getSupplier().getName() : null,
                b.getPurchaseOrderId(),
                b.getManufacturingDate(), b.getReceivedDate(), b.getExpiryDate(),
                b.getReceivedQty(), b.getRemainingQty(),
                b.getHeatNumber(), b.getCertificateNumber(),
                b.getUnitCost(), b.getStatus());
    }
}
