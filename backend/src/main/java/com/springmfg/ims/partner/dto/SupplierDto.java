package com.springmfg.ims.partner.dto;

import com.springmfg.ims.partner.domain.Supplier;

public record SupplierDto(
        Long id,
        String supplierCode,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        String gstNumber,
        String paymentTerms,
        Integer leadTimeDays,
        String status,
        Long version) {

    public static SupplierDto from(Supplier s) {
        return new SupplierDto(s.getId(), s.getSupplierCode(), s.getName(),
            s.getContactPerson(), s.getPhone(), s.getEmail(), s.getAddress(),
            s.getGstNumber(), s.getPaymentTerms(), s.getLeadTimeDays(),
            s.getStatus(), s.getVersion());
    }
}
