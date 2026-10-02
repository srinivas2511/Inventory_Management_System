package com.springmfg.ims.partner.dto;

import com.springmfg.ims.partner.domain.Customer;

public record CustomerDto(
        Long id,
        String customerCode,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        String gstNumber,
        String paymentTerms,
        String status,
        Long version) {

    public static CustomerDto from(Customer c) {
        return new CustomerDto(c.getId(), c.getCustomerCode(), c.getName(),
            c.getContactPerson(), c.getPhone(), c.getEmail(), c.getAddress(),
            c.getGstNumber(), c.getPaymentTerms(), c.getStatus(), c.getVersion());
    }
}
