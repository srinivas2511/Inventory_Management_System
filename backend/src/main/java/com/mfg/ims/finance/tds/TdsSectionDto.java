package com.mfg.ims.finance.tds;

import java.math.BigDecimal;

public record TdsSectionDto(Long id, String section, String description, BigDecimal rate) {}
