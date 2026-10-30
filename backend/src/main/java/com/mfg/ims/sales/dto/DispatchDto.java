package com.mfg.ims.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DispatchDto(
        Long id,
        String dispatchNumber,
        Long salesOrderId,
        String orderNumber,
        Long customerId,
        String customerName,
        LocalDate dispatchDate,
        String transporter,
        String vehicleNo,
        String lrNumber,
        String deliveryAddress,
        String invoiceReference,
        String status,
        String notes,
        List<DispatchItemDto> items,
        Map<String, Object> packagingDetails) {

    public record DispatchItemDto(
            Long id,
            Long productId,
            String productCode,
            String productName,
            Long fgBatchId,
            String batchNumber,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String customerSpecRef) {}
}
