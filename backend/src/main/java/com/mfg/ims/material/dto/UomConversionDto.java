package com.mfg.ims.material.dto;

import java.math.BigDecimal;

/** {@code 1 altUom = factorToBase x baseUom}, e.g. 1 COIL = 250 KG. */
public record UomConversionDto(Long id, Long materialId, String baseUom, String altUom, BigDecimal factorToBase) {
}
