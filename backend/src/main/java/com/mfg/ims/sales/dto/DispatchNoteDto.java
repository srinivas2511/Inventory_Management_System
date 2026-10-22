package com.mfg.ims.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DispatchNoteDto(
        String dispatchNumber,
        String orderNumber,
        String customerName,
        String customerGst,
        LocalDate dispatchDate,
        String transporter,
        String vehicleNo,
        String lrNumber,
        String deliveryAddress,
        String invoiceReference,
        List<DispatchNoteLineDto> lines,
        BigDecimal totalQty,
        BigDecimal totalValue) {

    public record DispatchNoteLineDto(
            int seq,
            String productCode,
            String productName,
            String batchNumber,
            BigDecimal quantity,
            String uom,
            BigDecimal unitPrice,
            BigDecimal lineValue,
            String customerPartNumber,
            String drawingNumber) {}
}
