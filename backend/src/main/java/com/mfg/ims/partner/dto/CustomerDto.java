package com.mfg.ims.partner.dto;

import com.mfg.ims.partner.domain.Customer;

public record CustomerDto(
        Long id,
        String customerCode,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        String gstNumber,
        String currencyCode,
        String paymentTerms,
        String status,
        String regionCode,
        String state,
        String city,
        String country,
        Long version) {

    public static CustomerDto from(Customer c) {
        return new CustomerDto(c.getId(), c.getCustomerCode(), c.getName(),
            c.getContactPerson(), c.getPhone(), c.getEmail(), c.getAddress(),
            c.getGstNumber(), c.getCurrencyCode(), c.getPaymentTerms(), c.getStatus(),
            c.getRegionCode(), c.getState(), c.getCity(), c.getCountry(), c.getVersion());
    }
}
