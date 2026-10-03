package com.springmfg.ims.crm.dto;

public record ContactDto(
        Long id,
        Long customerId,
        String customerName,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phone,
        String designation,
        String department,
        boolean active,
        Long version) {}
