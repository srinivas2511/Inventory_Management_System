package com.springmfg.ims.crm.dto;

import java.math.BigDecimal;

/** source is CUSTOMER or STANDARD; unitPrice is null (source NONE) when no price list row applies. */
public record ResolvedPriceDto(BigDecimal unitPrice, String source, Long priceId) {}
