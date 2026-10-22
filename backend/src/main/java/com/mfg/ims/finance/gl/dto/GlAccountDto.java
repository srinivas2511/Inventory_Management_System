package com.mfg.ims.finance.gl.dto;

public record GlAccountDto(
        Long id,
        String accountCode,
        String name,
        String accountType,
        String normalBalance,
        boolean allowDirectPosting,
        boolean active
) {}
