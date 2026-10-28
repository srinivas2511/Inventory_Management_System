package com.mfg.ims.production.barcode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GenerateBarcodeRequest(
        @NotBlank String labelType,
        @NotBlank String refType,
        @NotNull Long refId,
        String qrData
) {}
