package com.mfg.ims.sales.quotation;

/** Returned by POST /api/sales/quotations/{id}/convert-to-oc */
public record QuotationConversionResultDto(Long ocId, String ocNumber) {}
