package com.mfg.ims.procurement.dto;

import java.time.LocalDate;

public record GrnPendingDto(
        Long grnId,
        String grnNumber,
        Long supplierId,
        String supplierName,
        LocalDate receiptDate,
        String status
) {}
