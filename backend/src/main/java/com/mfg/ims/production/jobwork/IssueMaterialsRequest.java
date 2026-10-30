package com.mfg.ims.production.jobwork;

import java.math.BigDecimal;
import java.util.List;

public record IssueMaterialsRequest(List<IssueLine> lines) {
    public record IssueLine(Long materialId, BigDecimal qty, Long locationId, String notes) {}
}
