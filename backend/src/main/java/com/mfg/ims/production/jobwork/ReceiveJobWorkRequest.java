package com.mfg.ims.production.jobwork;

import java.math.BigDecimal;

public record ReceiveJobWorkRequest(
        BigDecimal receivedQty,
        BigDecimal scrapQty,
        String qualityStatus,
        String notes
) {}
