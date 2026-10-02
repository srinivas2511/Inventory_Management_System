package com.springmfg.ims.sales.dto;

public record CustomerProductSpecDto(
        Long id,
        Long customerId,
        Long productId,
        String productCode,
        String productName,
        String specReference,
        String drawingNumber,
        String customerPartNumber,
        String notes,
        boolean active) {}
