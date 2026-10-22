package com.mfg.ims.partner.dto;

/** One entry of an approved list: a material on a supplier's list, or a product on a customer's list. */
public record ApprovedItemDto(Long id, String code, String name) {
}
