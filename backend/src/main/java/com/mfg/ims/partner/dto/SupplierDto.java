package com.mfg.ims.partner.dto;

import com.mfg.ims.partner.domain.Supplier;

public record SupplierDto(
        Long id,
        String supplierCode,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        String addressLine1,
        String addressLine2,
        String city,
        String stateName,
        String pinCode,
        String country,
        String pan,
        String gstNumber,
        String currencyCode,
        String paymentTerms,
        Long paymentTermsId,
        Integer leadTimeDays,
        String status,
        String notes,
        Long vendorTypeId,
        Long version) {

    public static SupplierDto from(Supplier s) {
        return new SupplierDto(s.getId(), s.getSupplierCode(), s.getName(),
            s.getContactPerson(), s.getPhone(), s.getEmail(), s.getAddress(),
            s.getAddressLine1(), s.getAddressLine2(), s.getCity(),
            s.getStateName(), s.getPinCode(), s.getCountry(), s.getPan(),
            s.getGstNumber(), s.getCurrencyCode(), s.getPaymentTerms(), s.getPaymentTermsId(),
            s.getLeadTimeDays(), s.getStatus(), s.getNotes(), s.getVendorTypeId(), s.getVersion());
    }
}
