package com.springmfg.ims.masterdata;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes shared by {@code /api/suppliers} and {@code /api/customers}. */
public final class PartnerDtos {

    /** 15-character GSTIN: state code, PAN, entity number, 'Z', check character (DESIGN.md section 5.2). */
    public static final String GST_PATTERN = "\\d{2}[A-Z]{5}\\d{4}[A-Z]\\d[Z][A-Z\\d]";
    public static final String CODE_PATTERN = "[A-Z0-9][A-Z0-9._-]{1,19}";
    public static final String PHONE_PATTERN = "[0-9+() -]{5,20}";

    private PartnerDtos() {
    }

    /** Compact reference used inside other resources. */
    public record PartnerRef(long id, String code, String name) {
    }

    public record PartnerSummary(long id, String code, String name, String contactPerson, String phone, String email,
            String gstNumber, boolean active) {
    }

    public record PartnerResponse(long id, String code, String name, String contactPerson, String phone, String email,
            String address, String gstNumber, String paymentTerms, Integer leadTimeDays, boolean active, long version,
            Instant createdAt, Instant updatedAt) {
    }

    public record CreatePartnerRequest(
            @NotBlank @Pattern(regexp = CODE_PATTERN, message = "2-20 characters: A-Z, 0-9, dot, underscore or hyphen, upper case") String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 100) String contactPerson,
            @Pattern(regexp = PHONE_PATTERN, message = "5-20 characters: digits, +, (, ), - and spaces") String phone,
            @Email @Size(max = 160) String email,
            @Size(max = 400) String address,
            @Pattern(regexp = GST_PATTERN, message = "Not a valid 15-character GST number") String gstNumber,
            @Size(max = 60) String paymentTerms,
            @Min(0) Integer leadTimeDays) {
    }

    /** Full replacement of the editable fields; the code never changes. */
    public record UpdatePartnerRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 100) String contactPerson,
            @Pattern(regexp = PHONE_PATTERN, message = "5-20 characters: digits, +, (, ), - and spaces") String phone,
            @Email @Size(max = 160) String email,
            @Size(max = 400) String address,
            @Pattern(regexp = GST_PATTERN, message = "Not a valid 15-character GST number") String gstNumber,
            @Size(max = 60) String paymentTerms,
            @Min(0) Integer leadTimeDays,
            @NotNull Long version) {
    }
}
