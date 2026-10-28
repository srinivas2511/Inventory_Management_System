package com.mfg.ims.production.barcode;

import java.time.Instant;

public record BarcodeLabelDto(
        Long id,
        String labelType,
        String refType,
        Long refId,
        String barcodeValue,
        String qrData,
        Instant printedAt,
        int printCount,
        Instant createdAt
) {}
