package com.mfg.ims.sales.dto;

public record SaveCustomerProductSpecRequest(
        String specReference,
        String drawingNumber,
        String customerPartNumber,
        String notes) {}
