package com.mfg.ims.procurement.dto;

import java.math.BigDecimal;

public record PoBalanceDto(
        Long lineId,
        Long poId,
        String poNumber,
        Long materialId,
        String materialName,
        BigDecimal orderedQty,
        BigDecimal receivedQty,
        BigDecimal balanceQty,
        BigDecimal unitPrice
) {}
