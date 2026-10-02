package com.springmfg.ims.production.dto;

import java.math.BigDecimal;

public record WipDto(Long operationId, String operationName, int sequenceNo, BigDecimal wipQty) {}
