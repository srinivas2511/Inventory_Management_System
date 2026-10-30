package com.mfg.ims.engineering.bom;

import java.math.BigDecimal;
import java.util.List;

public record BomExplosionLineDto(
        int level,
        Long materialId,
        String materialCode,
        String materialName,
        String uom,
        BigDecimal totalQty,
        List<AlternateItem> alternates) {

    public record AlternateItem(Long materialId, String materialCode, String materialName, BigDecimal factor) {}
}
