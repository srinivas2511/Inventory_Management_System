package com.springmfg.ims.sales.dto;

public record SaveCustomerProductSpecRequest(
        String specReference,
        String drawingNumber,
        String customerPartNumber,
        String notes) {}
