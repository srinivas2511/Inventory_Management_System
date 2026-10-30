package com.mfg.ims.engineering.bom;

import java.math.BigDecimal;
import java.util.List;

public record BomExplosionDto(
        Long bomId,
        String bomNumber,
        BigDecimal requestedQty,
        List<BomExplosionLineDto> lines) {}
