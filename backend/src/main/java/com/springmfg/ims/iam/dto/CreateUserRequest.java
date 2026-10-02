package com.springmfg.ims.iam.dto;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @Size(max = 20) String employeeCode,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 160) String email,
        @Size(max = 20) String phone,
        @NotBlank @Size(min = 10, max = 100) String password,
        Set<String> roleCodes) {
}
