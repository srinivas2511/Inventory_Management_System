package com.springmfg.ims.material.dto;

/** MDM-004: the purchasing view of a material - preferred supplier, the unit it is bought in, its default tax code. */
public record PurchasingInfoDto(Long materialId, String baseUom, Long preferredSupplierId, String preferredSupplierName,
                                String purchaseUom, String purchaseTaxCode) {
}
