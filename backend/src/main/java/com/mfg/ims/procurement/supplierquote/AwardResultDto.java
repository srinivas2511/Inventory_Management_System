package com.mfg.ims.procurement.supplierquote;

/** Returned by the award endpoint: identifies the purchase order created from the winning quote. */
public record AwardResultDto(Long poId, String poNumber) {}
