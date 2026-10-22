package com.mfg.ims.engineering.bom;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateBomRequest(
        @NotNull Long productId,
        @Positive BigDecimal baseQuantity,
        String changeReason,
        List<BomItemRequest> items) {}
